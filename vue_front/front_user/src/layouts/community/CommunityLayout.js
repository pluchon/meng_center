import { defineAsyncComponent, defineComponent, onMounted, onUnmounted, watch, ref, nextTick } from 'vue'
import { IconCoin } from '@tabler/icons-vue'
import { growthApi } from '@/api/growth'
import { useCommunity } from '@/composables/useCommunity'
import { useSlider } from '@/composables/useSlider'
import { useUserStore } from '@/stores/user'
import { useGrowthStore } from '@/stores/growth'
import { useImStore } from '@/stores/im'
import { RouteName } from '@/router/names'
import { useRoute, useRouter } from 'vue-router'
import { Search, PostSort } from '@/constants/content'
import { Contact, LegalDocument } from '@/constants/app'
import { ElMessage } from 'element-plus'
import { Growth } from '@/constants/content'
import {
  userAvatar,
  brandIcon,
  communitySparkle,
  navHomeIcon,
  navCreateIcon,
  navGrowthIcon,
  navSearchIcon,
  navMessageIcon,
  communityFooterArt,
} from '@/assets/images'
export default defineComponent({
  name: 'CommunityLayout',
  components: {
    IconCoin,
    PointsDialog: defineAsyncComponent(() => import('@/components/points-dialog/PointsDialog.vue')),
  },
  setup() {
    const { t } = useCommunity()
    const user = useUserStore(),
      growth = useGrowthStore()
    const im = useImStore()
    const route = useRoute(),
      router = useRouter(),
      searchText = ref('')
    watch(
      () => route.query.q,
      (value) => {
        searchText.value = typeof value === 'string' ? value : ''
      },
      { immediate: true },
    )
    function submitSearch() {
      const q = searchText.value.trim()
      if (!q) return
      if (Array.from(q).length > Search.QUERY_MAX) {
        ElMessage.warning(t('search.tooLong', { max: Search.QUERY_MAX }))
        return
      }
      void router.push({ name: RouteName.SEARCH, query: { q, sort: PostSort.RELEVANT, page: '1' } })
    }
    const searchOpen = ref(false),
      searchInput = ref(null),
      searchHovered = ref(false)
    function leaveSearch(event) {
      searchHovered.value = false
      if (!event.currentTarget.contains(document.activeElement)) searchOpen.value = false
    }
    function blurSearch(event) {
      if (!searchHovered.value && !event.currentTarget.contains(event.relatedTarget)) {
        searchOpen.value = false
      }
    }
    function closeSearch() {
      searchOpen.value = false
      searchInput.value?.blur()
    }
    async function activateSearch() {
      if (searchOpen.value) {
        searchInput.value?.focus()
        return
      }
      searchOpen.value = true
      await nextTick()
      searchInput.value?.focus()
    }
    // 导航滑块：跟随悬停项，移开后回到当前页
    const navRoot = ref(null)
    const navSlider = useSlider(navRoot, { item: '[data-slide]', deps: () => route.name })
    let timer
    const refresh = () => {
      if (user.loggedIn && !document.hidden) growth.load()
    }
    watch(
      () => user.current?.id,
      () => {
        growth.clear()
        refresh()
      },
      { immediate: true },
    )
    // 导航上的积分：跟着经验变化重新取（签到、发帖、评论会同时加经验和积分），关掉积分弹窗后也重取
    const points = ref(0),
      pointsOpen = ref(false)
    async function loadPoints() {
      if (!user.loggedIn) return
      try {
        points.value = (await growthApi.points({ silent: true })).balance ?? 0
      } catch {
        // 积分只是导航上的小标，取不到就保留上一次的数字
      }
    }
    function closePoints() {
      pointsOpen.value = false
      void loadPoints()
    }
    watch(() => [user.current?.id, growth.data?.exp], loadPoints, { immediate: true })
    watch(
      () => growth.data?.level,
      (level, previous) => {
        if (previous && level > previous) ElMessage.success(t('home.levelUp', { level }))
      },
    )
    onMounted(() => {
      timer = setInterval(refresh, Growth.REFRESH_MS)
      document.addEventListener('visibilitychange', refresh)
    })
    onUnmounted(() => {
      clearInterval(timer)
      document.removeEventListener('visibilitychange', refresh)
    })
    return {
      route,
      t,
      Contact,
      points,
      pointsOpen,
      closePoints,
      navRoot,
      navSlider,
      searchOpen,
      searchText,
      submitSearch,
      searchInput,
      closeSearch,
      searchHovered,
      leaveSearch,
      blurSearch,
      activateSearch,
      user,
      growth,
      im,
      RouteName,
      LegalDocument,
      userAvatar,
      brandIcon,
      communitySparkle,
      navHomeIcon,
      navCreateIcon,
      navGrowthIcon,
      navSearchIcon,
      navMessageIcon,
      communityFooterArt,
    }
  },
})
