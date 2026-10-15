import { computed, defineComponent, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { confirmDialog } from '@/composables/useConfirm'
import {
  IconPhoto as PictureIcon,
  IconX as Close,
  IconCheck as Check,
  IconLock as Lock,
  IconMail as Message,
  IconDeviceMobile as Iphone,
  IconDeviceDesktop as Monitor,
  IconDeviceLaptop as Laptop,
  IconBan as CircleClose,
} from '@tabler/icons-vue'
import { userApi } from '@/api/user'
import { contentApi } from '@/api/content'
import { imApi } from '@/api/im'
import { useUserStore } from '@/stores/user'
import { useRequestResource } from '@/composables/useRequestResource'
import { notifyFailure } from '@/composables/useFailureNotice'
import { Auth, Regions } from '@/constants/app'
import { RouteName } from '@/router/names'
import { Account } from '@/constants/account'
import { accountDate } from '@/utils/accountDate'
import CaptchaDialog from '@/components/captcha/CaptchaDialog.vue'
import PageState from '@/components/page-state/PageState.vue'
import FolderColorPicker from '@/components/folder-color-picker/FolderColorPicker.vue'
import MessageAvatar from '@/components/message-avatar/MessageAvatar.vue'
export default defineComponent({
  name: 'AccountDialogs',
  components: {
    PictureIcon,
    Close,
    Check,
    CaptchaDialog,
    PageState,
    MessageAvatar,
    FolderColorPicker,
  },
  props: {
    type: { type: String, required: true },
    profile: { type: Object, required: true },
  },
  emits: ['close', 'saved', 'changed'],
  setup(props, { emit }) {
    const { t } = useI18n(),
      router = useRouter(),
      user = useUserStore()
    const form = reactive({
      name: '',
      description: '',
      color: 'GOLD',
      oldPassword: '',
      password: '',
      confirmPassword: '',
      email: '',
      countryCode: '+886',
      phone: '',
      code: '',
    })
    const busy = ref(false),
      sending = ref(false),
      seconds = ref(0),
      captcha = ref(null),
      cover = ref(null),
      coverPreview = ref(''),
      pending = ref(null),
      page = ref(1)
    let timer,
      disposed = false
    const keys = {
      folder: 'create',
      password: 'editPassword',
      email: props.profile.email ? 'changeEmail' : 'bindEmail',
      phone: props.profile.phone ? 'changePhone' : 'bindPhone',
      devices: 'devices',
      blocks: 'blocks',
    }
    const icons = {
      password: Lock,
      email: Message,
      phone: Iphone,
      devices: Monitor,
      blocks: CircleClose,
    }
    const faces = {
      folder: 'account.bottomFace',
      password: 'account.passwordFace',
      email: 'account.bottomFace',
      phone: 'account.phoneFace',
      devices: 'account.deviceFace',
      blocks: 'account.blockFace',
    }
    function deviceIcon(item) {
      const name = item.name || ''
      if (/iphone|android|ios|手机/i.test(name)) return Iphone
      return /mac|laptop|笔记本/i.test(name) ? Laptop : Monitor
    }
    function activity(value) {
      const elapsed = Date.now() - new Date(value).getTime()
      if (elapsed >= 0 && elapsed < 60000) return t('account.now')
      if (elapsed >= 60000 && elapsed < 3600000)
        return t('account.minutesAgo', { count: Math.floor(elapsed / 60000) })
      if (elapsed >= 3600000 && elapsed < 86400000)
        return t('account.hoursAgo', { count: Math.floor(elapsed / 3600000) })
      return accountDate(value, t)
    }
    const { state: resource, load } = useRequestResource(
      (signal) =>
        props.type === 'devices'
          ? userApi.devices({ signal })
          : imApi.blocks({ page: page.value, size: 20 }, { signal }),
      props.type === 'devices' ? [] : { records: [], total: 0 },
    )
    const list = computed(() => (props.type === 'devices' ? resource.data : resource.data.records))
    function close(done) {
      if (busy.value || pending.value || sending.value) return
      emit('close')
      if (typeof done === 'function') done()
    }
    function clearCover() {
      if (coverPreview.value) URL.revokeObjectURL(coverPreview.value)
      cover.value = null
      coverPreview.value = ''
    }
    function selectCover(event) {
      const file = event.target.files[0]
      event.target.value = ''
      if (!file) return
      if (
        !['image/jpeg', 'image/png', 'image/webp'].includes(file.type) ||
        file.size > 5 * 1024 * 1024
      ) {
        ElMessage.warning(t('account.coverInvalid'))
        return
      }
      clearCover()
      cover.value = file
      coverPreview.value = URL.createObjectURL(file)
    }
    function contact() {
      return props.type === 'email'
        ? { email: form.email.trim() }
        : { countryCode: form.countryCode, phone: form.phone.trim() }
    }
    function validContact() {
      if (
        props.type === 'email'
          ? !Auth.EMAIL_PATTERN.test(form.email.trim())
          : !/^\d{5,15}$/.test(form.phone.trim())
      ) {
        ElMessage.warning(
          t(props.type === 'email' ? 'auth.validation.email' : 'auth.validation.phone'),
        )
        return false
      }
      return true
    }
    async function sendCode() {
      if (sending.value || seconds.value || !validContact()) return
      sending.value = true
      const target = contact()
      try {
        const ticket = await captcha.value.solve()
        if (!ticket || disposed) return
        await userApi.contactCode({ ...target, ticket })
        if (disposed) return
        seconds.value = 60
        timer = setInterval(() => {
          if (--seconds.value <= 0) clearInterval(timer)
        }, 1000)
        ElMessage.success(t('account.sent'))
      } catch (error) {
        notifyFailure(error)
      } finally {
        sending.value = false
      }
    }
    async function submit() {
      if (busy.value) return
      if (
        props.type === 'folder' &&
        (!form.name.trim() || Array.from(form.name.trim()).length > Account.NAME_MAX)
      ) {
        ElMessage.warning(t('account.invalidName'))
        return
      }
      if (props.type === 'password' && !Auth.PASSWORD_PATTERN.test(form.password)) {
        ElMessage.warning(t('account.passwordRule'))
        return
      }
      if (props.type === 'password' && form.password !== form.confirmPassword) {
        ElMessage.warning(t('account.passwordMismatch'))
        return
      }
      if (props.type === 'email' || props.type === 'phone') {
        if (!validContact()) return
        if (!Auth.CODE_PATTERN.test(form.code) || !form.password) {
          ElMessage.warning(t('account.invalidCode'))
          return
        }
      }
      busy.value = true
      try {
        if (props.type === 'folder') {
          const body = new FormData()
          body.append('name', form.name.trim())
          body.append('description', form.description.trim())
          body.append('color', form.color)
          if (cover.value) body.append('cover', cover.value)
          await contentApi.createFolder(body)
          ElMessage.success(t('account.folderCreated'))
          emit('saved')
        } else if (props.type === 'password') {
          await userApi.password({
            oldPassword: form.oldPassword,
            password: form.password,
            confirmPassword: form.confirmPassword,
          })
          user.clear()
          ElMessage.success(t('account.passwordChanged'))
          router.replace({ name: RouteName.LOGIN })
          emit('close')
        } else {
          await userApi[props.type]({ ...contact(), code: form.code, password: form.password })
          emit('saved', await userApi.me())
          ElMessage.success(t('account.saved'))
        }
      } catch (error) {
        notifyFailure(error)
      } finally {
        busy.value = false
      }
    }
    function confirm(key) {
      return confirmDialog({
        message: t(key),
        title: t('account.confirm'),
        confirmText: t('account.confirm'),
        cancelText: t('account.cancel'),
      })
    }
    async function remove(item) {
      if (pending.value) return
      if (props.type === 'devices' && !(await confirm('account.offlineConfirm'))) return
      pending.value = props.type === 'devices' ? item.id : item.user.id
      try {
        if (props.type === 'devices') await userApi.revokeDevice(item.id)
        else await imApi.unblock(item.user.id)
        await load()
        emit('changed', props.type)
      } catch (error) {
        notifyFailure(error)
      } finally {
        pending.value = null
      }
    }
    async function removeOthers() {
      if (pending.value || !(await confirm('account.offlineOthersConfirm'))) return
      pending.value = 'others'
      try {
        await userApi.revokeOthers()
        await load()
        emit('changed', 'devices')
      } catch (error) {
        notifyFailure(error)
      } finally {
        pending.value = null
      }
    }
    onMounted(() => {
      if (props.type === 'devices' || props.type === 'blocks') void load()
    })
    onUnmounted(() => {
      disposed = true
      clearInterval(timer)
      clearCover()
    })
    return {
      t,
      title: computed(() => t('account.' + keys[props.type])),
      dialogIcon: computed(() => icons[props.type]),
      dialogFace: computed(() => t(faces[props.type])),
      confirmText: computed(() => {
        if (props.type === 'folder') return t('account.confirmCreate')
        if (props.type === 'password') return t('account.confirmPasswordChange')
        if (props.type === 'phone')
          return t(props.profile.phone ? 'account.confirmPhoneChange' : 'account.confirmBind')
        if (props.type === 'email')
          return t(props.profile.email ? 'account.confirmEmailChange' : 'account.confirmBind')
        return t('account.confirm')
      }),
      deviceIcon,
      activity,
      form,
      busy,
      sending,
      seconds,
      captcha,
      cover,
      coverPreview,
      pending,
      page,
      resource,
      list,
      load,
      close,
      selectCover,
      clearCover,
      sendCode,
      submit,
      remove,
      removeOthers,
      Regions,
      Account,
      date: (value) => accountDate(value, t),
    }
  },
})
