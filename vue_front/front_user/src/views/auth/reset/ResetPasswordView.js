import BorderGlow from '@/components/border-glow/BorderGlow.vue'
import CardEdge from '@/components/card-edge/CardEdge.vue'
import { useCodeCountdown } from '@/composables/useCodeCountdown'
import AuthDecor from '@/components/auth-decor/AuthDecor.vue'
import { AuthDecorVariant } from '@/constants/visual'
import { useAuthValidation } from '@/composables/useAuthValidation'
import { notifyFailure } from '@/composables/useFailureNotice'
import CapsLockHint from '@/components/caps-lock-hint/CapsLockHint.vue'
import FieldMessage from '@/components/field-message/FieldMessage.vue'
import { defineComponent, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/user'
import { Auth, CodeScope, ContactChannel, Regions } from '@/constants/app'
import { RouteName } from '@/router/names'
import ParticleBackground from '@/components/particle-background/ParticleBackground.vue'
import ContactField from '@/components/contact-field/ContactField.vue'
import VerificationCodeField from '@/components/verification-code-field/VerificationCodeField.vue'
import CaptchaDialog from '@/components/captcha/CaptchaDialog.vue'
import { resetAvatar as avatar } from '@/assets/images'
export default defineComponent({
  name: 'ResetPasswordView',
  components: {
    AuthDecor,
    BorderGlow,
    CapsLockHint,
    CardEdge,
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
      success = ref(false)
    const form = reactive({
      email: '',
      phone: '',
      countryCode: Regions[0].code,
      code: '',
      password: '',
      confirmPassword: '',
    })
    const { seconds, start: startCountdown, stop: stopCountdown } = useCodeCountdown(CodeScope.RESET)
    watch(channel, () => {
      form.code = ''
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
      try {
        if (!validateContact()) return
        const ticket = await captcha.value.solve()
        if (!ticket) return
        await userApi.resetCode({ ...contact(), ticket })
        ElMessage.success(t('auth.register.sent'))
        startCountdown()
      } catch (error) {
        if (!serverError(error)) notifyFailure(error)
      } finally {
        sending.value = false
      }
    }
    async function submit() {
      if (submitting.value || sending.value) return
      submitting.value = true
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
        stopCountdown()
      } catch (error) {
        if (!serverError(error)) notifyFailure(error)
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
