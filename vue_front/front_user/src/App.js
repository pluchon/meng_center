import { computed, defineComponent } from 'vue'
import { useRoute } from 'vue-router'
import { RouteName } from '@/router/names'
import AuthCardSwap from '@/components/auth-card-swap/AuthCardSwap.vue'
import GlowCursor from '@/components/glow-cursor/GlowCursor.vue'
import elementLocale from 'element-plus/es/locale/lang/zh-cn'
export default defineComponent({
  name: 'MengApplication',
  components: { GlowCursor, AuthCardSwap },
  setup() {
    const route = useRoute()
    const showCursor = computed(() =>
      [RouteName.LOGIN, RouteName.REGISTER, RouteName.RESET_PASSWORD].includes(route.name),
    )
    return { elementLocale, showCursor }
  },
})
