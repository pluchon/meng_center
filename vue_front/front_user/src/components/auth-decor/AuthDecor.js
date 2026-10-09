import { computed, defineComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { AuthDecorVariant } from '@/constants/visual'

// 各页面两张贴纸用的文案键
const stickerKeys = Object.freeze({
  [AuthDecorVariant.LOGIN]: ['auth.decor.welcome', 'auth.decor.wave'],
  [AuthDecorVariant.REGISTER]: ['auth.decor.join', 'auth.decor.cheer'],
  [AuthDecorVariant.RESET]: ['auth.decor.calm', 'auth.decor.pat'],
})

// 认证页表单空白处的小装饰，只负责好看：不接收指针，也不进入无障碍树
export default defineComponent({
  name: 'AuthDecor',
  props: {
    variant: {
      type: String,
      default: AuthDecorVariant.LOGIN,
      validator: (value) => Object.values(AuthDecorVariant).includes(value),
    },
  },
  setup(props) {
    return {
      t: useI18n().t,
      stickers: computed(() => stickerKeys[props.variant]),
    }
  },
})
