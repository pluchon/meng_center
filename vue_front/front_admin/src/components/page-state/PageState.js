import { defineComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouteName } from '@/router/names'
export default defineComponent({
  name: 'PageState',
  props: { state: { type: String, required: true } },
  emits: ['retry'],
  setup() {
    return { t: useI18n().t, RouteName }
  },
})
