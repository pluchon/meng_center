import { useAuthValidation } from '@/composables/useAuthValidation'
import FieldMessage from '@/components/field-message/FieldMessage.vue'
import { defineComponent, reactive, ref, onUnmounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/user'
import { Auth, ContactChannel, Regions, LegalVersion, LegalDocument } from '@/constants/app'
import { RouteName } from '@/router/names'
import AuthLayout from '@/layouts/auth/AuthLayout.vue'
import ContactField from '@/components/contact-field/ContactField.vue'
import VerificationCodeField from '@/components/verification-code-field/VerificationCodeField.vue'
import ReasonEditor from '@/components/reason-editor/ReasonEditor.vue'
import LegalConsent from '@/components/legal-consent/LegalConsent.vue'
import CaptchaDialog from '@/components/captcha/CaptchaDialog.vue'
export default defineComponent({
  name: 'RegisterView',
  components: {
    FieldMessage,
    AuthLayout,
    CaptchaDialog,
    LegalConsent,
    ReasonEditor,
    ContactField,
    VerificationCodeField,
  },
  setup() {
    const { t } = useI18n(),
      channel = ref(ContactChannel.SMS),
      agreed = ref(false),
      captcha = ref(null),
      submitting = ref(false),
      sending = ref(false),
      seconds = ref(0),
      failure = ref(''),
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
    let countdown
    onUnmounted(() => clearInterval(countdown))
    watch(channel, () => {
      form.code = ''
      failure.value = ''
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
      failure.value = ''
      try {
        if (!validateContact()) return
        const ticket = await captcha.value.solve()
        if (!ticket) return
        const send = channel.value === ContactChannel.EMAIL ? userApi.emailCode : userApi.smsCode
        await send({ ...contact(), ticket })
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
      if (submitting.value) return
      submitting.value = true
      failure.value = ''
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
      agreed,
      captcha,
      submitting,
      sending,
      seconds,
      failure,
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
