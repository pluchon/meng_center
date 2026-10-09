import { defineComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { LegalDocument } from '@/constants/app'
import { RouteName } from '@/router/names'
export default defineComponent({
  name: 'LegalConsent',
  props: { modelValue: Boolean, origin: { type: String, default: RouteName.REGISTER } },
  emits: ['update:modelValue'],
  setup() {
    return { t: useI18n().t, RouteName, LegalDocument }
  },
})
