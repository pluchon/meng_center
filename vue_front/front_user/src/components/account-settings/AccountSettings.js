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
    IconCheck,
  },
  props: { profile: { type: Object, required: true } },
  emits: ['saved', 'dialog'],
  setup(props, { emit, expose }) {
    const { t } = useI18n(),
      form = reactive({ nickname: '', bio: '', username: '' }),
      // 正在保存的是哪一项
      saving = ref(null)
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
    // 每一项改过才在输入框里、字数右边出现自己的确定按钮
    const dirty = (key) => form[key].trim() !== fields(props.profile)[key]
    const length = (key) => Array.from(form[key]).length
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
    function valid(key, text) {
      if (key === 'nickname' && text && (Array.from(text).length < 2 || Array.from(text).length > 16))
        return 'account.invalidNickname'
      if (key === 'bio' && (Array.from(text).length > Account.BIO_MAX || /[\r\n]/.test(text)))
        return 'account.invalidBio'
      if (key === 'username' && !Auth.USERNAME_PATTERN.test(text)) return 'account.invalidUsername'
      return null
    }
    // 只提交这一项，其余两项用已保存的值，别的输入框里没确定的改动不会被顺带存掉
    async function saveField(key) {
      if (saving.value || !dirty(key)) return
      let text = form[key].trim()
      if (key === 'username') text = text.toLowerCase()
      const problem = valid(key, text)
      if (problem) {
        ElMessage.warning(t(problem))
        return
      }
      saving.value = key
      try {
        await userApi.profile({ ...fields(props.profile), [key]: text })
        form[key] = text
        emit('saved', await userApi.me())
        ElMessage.success(t('account.saved'))
      } catch (error) {
        notifyFailure(error)
      } finally {
        saving.value = null
      }
    }
    return {
      t,
      form,
      saving,
      dirty,
      length,
      cooldown,
      availableDate,
      saveField,
      devices: devices.state,
      blocks: blocks.state,
      Account,
    }
  },
})
