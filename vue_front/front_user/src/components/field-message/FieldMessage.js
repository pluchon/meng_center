import { defineComponent } from 'vue'
export default defineComponent({
  name: 'FieldMessage',
  props: { message: { type: String, default: '' }, messageId: { type: String, required: true } },
})
