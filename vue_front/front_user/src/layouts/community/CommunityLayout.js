import { defineComponent, onMounted, onUnmounted, watch, ref, nextTick } from 'vue'
import { useCommunity } from '@/composables/useCommunity'
import { useSlider } from '@/composables/useSlider'
import { useUserStore } from '@/stores/user'
import { useGrowthStore } from '@/stores/growth'
import { useImStore } from '@/stores/im'
import { RouteName } from '@/router/names'
import { useRoute, useRouter } from 'vue-router'
import { Search, PostSort } from '@/constants/content'
import { LegalDocument } from '@/constants/app'
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
  setup() {
    const { t, unavailable } = useCommunity()
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
      unavailable,
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
