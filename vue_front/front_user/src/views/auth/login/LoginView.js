import { useAuthValidation } from '@/composables/useAuthValidation'
import { notifyFailure } from '@/composables/useFailureNotice'
import CapsLockHint from '@/components/caps-lock-hint/CapsLockHint.vue'
import FieldMessage from '@/components/field-message/FieldMessage.vue'
import { defineComponent, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { userApi } from '@/api/user'
import { RouteName } from '@/router/names'
import { Auth } from '@/constants/app'
import AuthLayout from '@/layouts/auth/AuthLayout.vue'
import LegalConsent from '@/components/legal-consent/LegalConsent.vue'
import ReasonEditor from '@/components/reason-editor/ReasonEditor.vue'
import CaptchaDialog from '@/components/captcha/CaptchaDialog.vue'
export default defineComponent({
  name: 'LoginView',
  components: {
    CapsLockHint,
    FieldMessage,
    AuthLayout,
    CaptchaDialog,
    LegalConsent,
    ReasonEditor,
  },
  setup() {
    const { t } = useI18n(),
      router = useRouter(),
      route = useRoute(),
      user = useUserStore()
    const form = reactive({ username: '', password: '', applyReason: '' }),
      agreed = ref(false),
      captcha = ref(null),
      submitting = ref(false),
      pending = ref(false),
      rejected = ref(false),
      rejectionReason = ref('')
    const { errors, fieldId, validate, serverError } = useAuthValidation(form, { agreed })
    async function submit() {
      if (submitting.value) return
      if (!validate('login')) return
      pending.value = false
      rejected.value = false
      submitting.value = true
      try {
        const ticket = await captcha.value.solve()
        if (!ticket) return
        await user.login({ username: form.username, password: form.password, ticket })
        const destination = route.query.redirect
        await router.replace(
          typeof destination === 'string' &&
            destination.startsWith('/') &&
            !destination.startsWith('//')
            ? destination
            : { name: RouteName.ACCOUNT },
        )
      } catch (error) {
        if (error.code === 'USER_PENDING') pending.value = true
        else if (error.code === 'USER_REJECTED') {
          rejected.value = true
          rejectionReason.value = error.data?.reason || error.data?.rejectReason || ''
        } else if (!serverError(error)) notifyFailure(error)
      } finally {
        submitting.value = false
      }
    }
    async function reapply() {
      if (submitting.value) return
      submitting.value = true
      try {
        if (!validate('login')) return
        const ticket = await captcha.value.solve()
        if (!ticket) return
        await userApi.reapply({ ...form, ticket })
        rejected.value = false
        pending.value = true
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
      form,
      agreed,
      captcha,
      submitting,
      pending,
      rejected,
      rejectionReason,
      submit,
      reapply,
      Auth,
      RouteName,
    }
  },
})
