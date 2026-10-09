import { defineComponent, computed } from 'vue'
import FieldMessage from '@/components/field-message/FieldMessage.vue'
import { useI18n } from 'vue-i18n'
export default defineComponent({
  name: 'VerificationCodeField',
  components: { FieldMessage },
  props: {
    error: { type: String, default: '' },
    messageId: { type: String, required: true },
    modelValue: { type: String, default: '' },
    seconds: { type: Number, default: 0 },
    sending: Boolean,
    disabled: Boolean,
  },
  emits: ['update:modelValue', 'send'],
  setup(props, { emit }) {
    const { t } = useI18n()
    const code = computed({
      get: () => props.modelValue,
      set: (value) => emit('update:modelValue', value),
    })
    return { t, code }
  },
})
