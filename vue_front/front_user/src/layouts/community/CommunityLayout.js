import { defineComponent, onMounted, onUnmounted, watch, ref, nextTick } from 'vue'
import { House, Message, Search as SearchIcon, Bell } from '@element-plus/icons-vue'
import { useCommunity } from '@/composables/useCommunity'
import { useUserStore } from '@/stores/user'
import { useGrowthStore } from '@/stores/growth'
import { RouteName } from '@/router/names'
import { LegalDocument } from '@/constants/app'
import { ElMessage } from 'element-plus'
import { Growth } from '@/constants/content'
import {
  userAvatar,
  communityDrop,
  communitySparkle,
  communitySprout,
  communityFooterArt,
} from '@/assets/images'
export default defineComponent({
  name: 'CommunityLayout',
  components: { House, Message, SearchIcon, Bell },
  setup() {
    const { t, unavailable } = useCommunity()
    const user = useUserStore(),
      growth = useGrowthStore()
    const searchOpen = ref(false),
      searchInput = ref(null)
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
      t,
      unavailable,
      searchOpen,
      searchInput,
      closeSearch,
      activateSearch,
      user,
      growth,
      RouteName,
      LegalDocument,
      userAvatar,
      communityDrop,
      communitySparkle,
      communitySprout,
      communityFooterArt,
    }
  },
})
