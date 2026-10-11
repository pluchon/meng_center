import { defineComponent } from 'vue'
import { Minus } from '@element-plus/icons-vue'
import { useCommunity } from '@/composables/useCommunity'
export default defineComponent({
  name: 'CollapsibleCard',
  props: {
    collapsed: { type: Boolean, default: false },
    label: { type: String, required: true },
    icon: { type: Object, required: true },
  },
  emits: ['update:collapsed'],
  setup() {
    return { ...useCommunity(), Minus }
  },
})
