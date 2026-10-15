import { computed, defineComponent, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  IconMoodSmile as User,
  IconPencil as EditPen,
  IconLock as Lock,
  IconDeviceDesktop as Monitor,
  IconAt as UserFilled,
  IconMail as Message,
  IconDeviceMobile as Iphone,
  IconBan as CircleClose,
  IconLogout,
  IconCheck,
} from '@tabler/icons-vue'
import { userApi } from '@/api/user'
import { notifyFailure } from '@/composables/useFailureNotice'
import { Auth } from '@/constants/app'
import { imApi } from '@/api/im'
import { useRequestResource } from '@/composables/useRequestResource'
import { Account } from '@/constants/account'
export default defineComponent({
  name: 'AccountSettings',
  components: {
    User,
    EditPen,
    Lock,
    Monitor,
    UserFilled,
    Message,
    Iphone,
    CircleClose,
    IconLogout,
    IconCheck,
  },
  props: { profile: { type: Object, required: true }, loggingOut: Boolean },
  emits: ['saved', 'dialog', 'logout'],
  setup(props, { emit, expose }) {
    const { t } = useI18n(),
      form = reactive({ nickname: '', bio: '', username: '' }),
      saving = ref(false)
    const devices = useRequestResource((signal) => userApi.devices({ signal }), null)
    const blocks = useRequestResource(
      (signal) => imApi.blocks({ page: 1, size: 1 }, { signal }),
      null,
    )
    function refresh(type) {
      if (!type || type === 'devices') void devices.load()
      if (!type || type === 'blocks') void blocks.load()
    }
    expose({ refresh })
    onMounted(() => refresh())
    function fields(profile) {
      return {
        nickname: profile.nickname || '',
        bio: profile.bio || '',
        username: profile.username,
      }
    }
    watch(
      () => props.profile,
      (value, previous) => {
        const old = previous ? fields(previous) : null
        for (const [key, text] of Object.entries(fields(value)))
          if (!old || form[key] === old[key]) form[key] = text
      },
      { immediate: true },
    )
    const changed = computed(() =>
      Object.entries(fields(props.profile)).some(([key, value]) => form[key].trim() !== value),
    )
    const cooldown = computed(
      () =>
        props.profile.usernameAvailableAt &&
        new Date(props.profile.usernameAvailableAt) > new Date(),
    )
    const availableDate = computed(() =>
      props.profile.usernameAvailableAt
        ? new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric' }).format(
            new Date(props.profile.usernameAvailableAt),
          )
        : '',
    )
    async function save() {
      if (saving.value || !changed.value) return
      const value = Object.fromEntries(
        Object.entries(form).map(([key, text]) => [key, text.trim()]),
      )
      value.username = value.username.toLowerCase()
      if (
        value.nickname &&
        (Array.from(value.nickname).length < 2 || Array.from(value.nickname).length > 16)
      ) {
        ElMessage.warning(t('account.invalidNickname'))
        return
      }
      if (Array.from(value.bio).length > Account.BIO_MAX || /[\r\n]/.test(form.bio)) {
        ElMessage.warning(t('account.invalidBio'))
        return
      }
      if (!Auth.USERNAME_PATTERN.test(value.username)) {
        ElMessage.warning(t('account.invalidUsername'))
        return
      }
      saving.value = true
      try {
        await userApi.profile(value)
        Object.assign(form, value)
        emit('saved', await userApi.me())
        ElMessage.success(t('account.saved'))
      } catch (error) {
        notifyFailure(error)
      } finally {
        saving.value = false
      }
    }
    return {
      t,
      form,
      saving,
      changed,
      cooldown,
      availableDate,
      save,
      devices: devices.state,
      blocks: blocks.state,
      Account,
    }
  },
})
