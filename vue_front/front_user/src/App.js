import { defineComponent } from 'vue'
import AuthCardSwap from '@/components/auth-card-swap/AuthCardSwap.vue'
import GlowCursor from '@/components/glow-cursor/GlowCursor.vue'
import elementLocale from 'element-plus/es/locale/lang/zh-cn'
export default defineComponent({
  name: 'MengApplication',
  components: { GlowCursor, AuthCardSwap },
  setup() {
    return { elementLocale }
  },
})
