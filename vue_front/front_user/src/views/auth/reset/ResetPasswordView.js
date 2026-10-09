import BorderGlow from '@/components/border-glow/BorderGlow.vue'
import AuthDecor from '@/components/auth-decor/AuthDecor.vue'
import { AuthDecorVariant } from '@/constants/visual'
import { useAuthValidation } from '@/composables/useAuthValidation'
import FieldMessage from '@/components/field-message/FieldMessage.vue'
import { defineComponent, reactive, ref, watch, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/user'
import { Auth, ContactChannel, Regions } from '@/constants/app'
import { RouteName } from '@/router/names'
import ParticleBackground from '@/components/particle-background/ParticleBackground.vue'
import ContactField from '@/components/contact-field/ContactField.vue'
import VerificationCodeField from '@/components/verification-code-field/VerificationCodeField.vue'
import CaptchaDialog from '@/components/captcha/CaptchaDialog.vue'
import avatar from '@/assets/forget_password_avatar.png'
export default defineComponent({
  name: 'ResetPasswordView',
  components: {
    AuthDecor,
    BorderGlow,
    FieldMessage,
    ParticleBackground,
    ContactField,
    VerificationCodeField,
    CaptchaDialog,
  },
  setup() {
    const { t } = useI18n(),
      channel = ref(ContactChannel.EMAIL),
      captcha = ref(null),
      sending = ref(false),
      submitting = ref(false),
      seconds = ref(0),
      failure = ref(''),
      success = ref(false)
    const form = reactive({
      email: '',
      phone: '',
      countryCode: Regions[0].code,
      code: '',
      password: '',
      confirmPassword: '',
    })
    let countdown
    onUnmounted(() => clearInterval(countdown))
    watch(channel, () => {
      form.code = ''
      failure.value = ''
    })
    const contact = () =>
      channel.value === ContactChannel.EMAIL
        ? { email: form.email }
        : { countryCode: form.countryCode, phone: form.phone }
    const { errors, fieldId, validate, validateContact, serverError } = useAuthValidation(form, {
      channel,
    })
    async function sendCode() {
      if (sending.value || seconds.value) return
      sending.value = true
      failure.value = ''
      try {
        if (!validateContact()) return
        const ticket = await captcha.value.solve()
        if (!ticket) return
        await userApi.resetCode({ ...contact(), ticket })
        ElMessage.success(t('auth.register.sent'))
        seconds.value = Auth.CODE_SECONDS
        countdown = setInterval(() => {
          if (--seconds.value <= 0) clearInterval(countdown)
        }, 1000)
      } catch (error) {
        if (!serverError(error)) failure.value = error.message
      } finally {
        sending.value = false
      }
    }
    async function submit() {
      if (submitting.value || sending.value) return
      submitting.value = true
      failure.value = ''
      try {
        if (!validate('reset')) return
        const ticket = await captcha.value.solve()
        if (!ticket) return
        await userApi.resetPassword({
          ...contact(),
          code: form.code,
          password: form.password,
          confirmPassword: form.confirmPassword,
          ticket,
        })
        form.password = ''
        form.confirmPassword = ''
        form.code = ''
        success.value = true
        clearInterval(countdown)
      } catch (error) {
        if (!serverError(error)) failure.value = error.message
      } finally {
        submitting.value = false
      }
    }
    return {
      errors,
      fieldId,
      t,
      channel,
      captcha,
      sending,
      submitting,
      seconds,
      failure,
      success,
      form,
      sendCode,
      submit,
      Auth,
      RouteName,
      AuthDecorVariant,
      avatar,
    }
  },
})
