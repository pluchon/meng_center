import { useAuthValidation } from '@/composables/useAuthValidation'
import { notifyFailure } from '@/composables/useFailureNotice'
import CapsLockHint from '@/components/caps-lock-hint/CapsLockHint.vue'
import { useCodeCountdown } from '@/composables/useCodeCountdown'
import FieldMessage from '@/components/field-message/FieldMessage.vue'
import { defineComponent, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/user'
import {
  Auth,
  CodeScope,
  ContactChannel,
  Regions,
  LegalVersion,
  LegalDocument,
} from '@/constants/app'
import { RouteName } from '@/router/names'
import { authHeartIcon, authLockIcon, authUserIcon } from '@/assets/images'
import AuthLayout from '@/layouts/auth/AuthLayout.vue'
import ContactField from '@/components/contact-field/ContactField.vue'
import VerificationCodeField from '@/components/verification-code-field/VerificationCodeField.vue'
import ReasonEditor from '@/components/reason-editor/ReasonEditor.vue'
import LegalConsent from '@/components/legal-consent/LegalConsent.vue'
import CaptchaDialog from '@/components/captcha/CaptchaDialog.vue'
export default defineComponent({
  name: 'RegisterView',
  components: {
    CapsLockHint,
    FieldMessage,
    AuthLayout,
    CaptchaDialog,
    LegalConsent,
    ReasonEditor,
    ContactField,
    VerificationCodeField,
  },
  setup() {
    const icons = { heart: authHeartIcon, lock: authLockIcon, user: authUserIcon }
    const { t } = useI18n(),
      channel = ref(ContactChannel.SMS),
      agreed = ref(false),
      captcha = ref(null),
      submitting = ref(false),
      sending = ref(false),
      success = ref(false)
    const form = reactive({
      username: '',
      password: '',
      confirmPassword: '',
      email: '',
      countryCode: Regions[0].code,
      phone: '',
      code: '',
      applyReason: '',
    })
    const { seconds, start: startCountdown } = useCodeCountdown(CodeScope.REGISTER)
    watch(channel, () => {
      form.code = ''
    })
    function contact() {
      return channel.value === ContactChannel.EMAIL
        ? { email: form.email }
        : { countryCode: form.countryCode, phone: form.phone }
    }
    const { errors, fieldId, validate, validateContact, serverError } = useAuthValidation(form, {
      channel,
      agreed,
    })
    async function sendCode() {
      if (sending.value || seconds.value) return
      sending.value = true
      try {
        if (!validateContact()) return
        const ticket = await captcha.value.solve()
        if (!ticket) return
        const send = channel.value === ContactChannel.EMAIL ? userApi.emailCode : userApi.smsCode
        await send({ ...contact(), ticket })
        ElMessage.success(t('auth.register.sent'))
        startCountdown()
      } catch (error) {
        if (!serverError(error)) notifyFailure(error)
      } finally {
        sending.value = false
      }
    }
    async function submit() {
      if (submitting.value) return
      submitting.value = true
      try {
        if (!validate('register')) return
        const ticket = await captcha.value.solve()
        if (!ticket) return
        await userApi.register({
          username: form.username,
          password: form.password,
          confirmPassword: form.confirmPassword,
          ...contact(),
          code: form.code,
          applyReason: form.applyReason,
          ticket,
          termsVersion: LegalVersion.TERMS,
          privacyVersion: LegalVersion.PRIVACY,
        })
        success.value = true
      } catch (error) {
        if (!serverError(error)) notifyFailure(error)
      } finally {
        submitting.value = false
      }
    }
    return {
      icons,
      errors,
      fieldId,
      t,
      channel,
      agreed,
      captcha,
      submitting,
      sending,
      seconds,
      success,
      form,
      sendCode,
      submit,
      Auth,
      ContactChannel,
      Regions,
      RouteName,
      LegalDocument,
    }
  },
})
