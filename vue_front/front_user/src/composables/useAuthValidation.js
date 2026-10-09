import { reactive, watch, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { Auth, ContactChannel } from '@/constants/app'

const serverFields = Object.freeze({
  USER_INVALID_CREDENTIALS: 'password',
  USER_USERNAME_TAKEN: 'username',
  USER_CONTACT_REQUIRED: 'contact',
  USER_CONTACT_INVALID: 'contact',
  USER_CONTACT_TAKEN: 'contact',
  USER_INVALID_CODE: 'code',
  USER_RESET_FAILED: 'code',
  USER_PASSWORD_MISMATCH: 'confirmPassword',
})
export function useAuthValidation(form, { channel, agreed } = {}) {
  const { t } = useI18n(),
    errors = reactive({}),
    prefix = useId()
  const fieldId = (field) => prefix + '-' + field
  const clearErrors = () => {
    for (const field of Object.keys(errors)) delete errors[field]
  }
  for (const field of Object.keys(form))
    watch(
      () => form[field],
      () => {
        delete errors[field]
        if (['email', 'phone', 'countryCode'].includes(field)) delete errors.contact
        if (field === 'password') delete errors.confirmPassword
      },
    )
  if (channel)
    watch(channel, () => {
      delete errors.contact
      delete errors.code
    })
  if (agreed) watch(agreed, () => delete errors.agreed)
  function contact() {
    delete errors.contact
    const email = channel.value === ContactChannel.EMAIL
    const value = email ? form.email.trim() : form.phone.trim()
    if (!value) errors.contact = t(email ? 'auth.validation.email' : 'auth.validation.phone')
    else if (email && !Auth.EMAIL_PATTERN.test(value))
      errors.contact = t('error.USER_CONTACT_INVALID')
    return !errors.contact
  }
  function password() {
    if (!form.password) errors.password = t('auth.validation.password')
    else if (!Auth.PASSWORD_PATTERN.test(form.password))
      errors.password = t('auth.register.passwordRule')
    if (!form.confirmPassword) errors.confirmPassword = t('auth.validation.confirmPassword')
    else if (form.password !== form.confirmPassword)
      errors.confirmPassword = t('auth.register.passwordMismatch')
  }
  function validate(kind) {
    clearErrors()
    if (kind === 'login' || kind === 'register') {
      if (!form.username.trim()) errors.username = t('auth.validation.username')
      else if (kind === 'register' && !Auth.USERNAME_PATTERN.test(form.username))
        errors.username = t('auth.register.usernameRule')
    }
    if (kind === 'login') {
      if (!form.password) errors.password = t('auth.validation.password')
    } else {
      contact()
      password()
      if (!Auth.CODE_PATTERN.test(form.code)) errors.code = t('auth.register.codeHint')
    }
    if (agreed && !agreed.value) errors.agreed = t('auth.register.mustAgree')
    return Object.keys(errors).length === 0
  }
  function serverError(error) {
    const field = serverFields[error.code]
    if (!field) return false
    errors[field] = error.message
    return true
  }
  return { errors, fieldId, validate, validateContact: contact, serverError }
}
