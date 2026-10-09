import { defineComponent, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { UserRole } from '@/constants/app'
import { RouteName } from '@/router/names'
import CaptchaDialog from '@/components/captcha/CaptchaDialog.vue'
export default defineComponent({
  name: 'LoginView',
  components: { CaptchaDialog },
  setup() {
    const { t } = useI18n(),
      router = useRouter(),
      user = useUserStore(),
      form = reactive({ username: '', password: '' }),
      captcha = ref(null),
      submitting = ref(false),
      failure = ref('')
    async function submit() {
      if (submitting.value) return
      submitting.value = true
      failure.value = ''
      try {
        const ticket = await captcha.value.solve()
        if (!ticket) return
        await user.login({ ...form, ticket })
        if (user.current.role !== UserRole.ADMIN) {
          await user.logout()
          failure.value = t('user.review.adminOnly')
          return
        }
        await router.replace({ name: RouteName.REVIEW })
      } catch (error) {
        failure.value = error.message
      } finally {
        submitting.value = false
      }
    }
    return { t, form, captcha, submitting, failure, submit }
  },
})
