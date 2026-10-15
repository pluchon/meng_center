import { defineComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { FolderColors } from '@/constants/account'
import { authTickIcon } from '@/assets/images'
export default defineComponent({
  name: 'FolderColorPicker',
  props: {
    modelValue: { type: String, default: 'GOLD' },
    disabled: Boolean,
  },
  emits: ['update:modelValue'],
  setup() {
    return { t: useI18n().t, FolderColors, tickIcon: authTickIcon }
  },
})
