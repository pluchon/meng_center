import { defineComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { LegalDocument } from '@/constants/app'
import { RouteName } from '@/router/names'
export default defineComponent({
  name: 'LegalConsent',
  props: {
    modelValue: Boolean,
    origin: { type: String, default: RouteName.REGISTER },
    // 未勾选就提交时为真：勾选行原地变红并换成提醒文案，不另起一行
    invalid: Boolean,
  },
  emits: ['update:modelValue'],
  setup() {
    return { t: useI18n().t, RouteName, LegalDocument }
  },
})
