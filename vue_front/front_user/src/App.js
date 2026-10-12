import { computed, defineComponent, watch, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useImStore } from '@/stores/im'
import { RouteName } from '@/router/names'
import AuthCardSwap from '@/components/auth-card-swap/AuthCardSwap.vue'
import GlowCursor from '@/components/glow-cursor/GlowCursor.vue'
import elementLocale from 'element-plus/es/locale/lang/zh-cn'
export default defineComponent({
  name: 'MengApplication',
  components: { GlowCursor, AuthCardSwap },
  setup() {
    const route = useRoute(),
      user = useUserStore(),
      im = useImStore()
    watch(
      () => user.current?.id,
      (id) => im.start(id),
      { immediate: true },
    )
    onUnmounted(() => im.stop())
    const showCursor = computed(() =>
      [RouteName.LOGIN, RouteName.REGISTER, RouteName.RESET_PASSWORD].includes(route.name),
    )
    return { elementLocale, showCursor }
  },
})
