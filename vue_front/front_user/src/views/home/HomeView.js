import { scrollToTop } from '@/composables/scrollToTop'
import { defineComponent, ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { contentApi } from '@/api/content'
import { Categories, PostSort, Feed } from '@/constants/content'
import { homeArtwork, communitySparkle } from '@/assets/images'
import HeroWaves from '@/components/hero-waves/HeroWaves.vue'
import CommunityLayout from '@/layouts/community/CommunityLayout.vue'
import PostCard from '@/components/post-card/PostCard.vue'
import FeedFilters from '@/components/feed-filters/FeedFilters.vue'
import FeedPagination from '@/components/feed-pagination/FeedPagination.vue'
import PageState from '@/components/page-state/PageState.vue'
const homeScrollbarClass = 'home-scrollbar-hidden'

export default defineComponent({
  name: 'HomeView',
  components: {
    HeroWaves,
    CommunityLayout,
    PostCard,
    FeedPagination,
    FeedFilters,
    PageState,
  },
  setup() {
    onMounted(() => document.documentElement.classList.add(homeScrollbarClass))
    onUnmounted(() => {
      document.documentElement.classList.remove(homeScrollbarClass)
      requestId++
      controller?.abort()
    })
    const route = useRoute(),
      router = useRouter(),
      records = ref([]),
      total = ref(0),
      loading = ref(false),
      error = ref(null),
      hasLoaded = ref(false),
      revision = ref(0),
      renderedPage = ref(1),
      feedRoot = ref(null)
    let requestId = 0
    let controller
    const query = computed(() => ({
      category: Categories.includes(route.query.category) ? route.query.category : undefined,
      sort: Object.values(PostSort).includes(route.query.sort) ? route.query.sort : PostSort.LATEST,
      page: Math.max(
        1,
        Number.isSafeInteger(Number(route.query.page)) ? Number(route.query.page) : 1,
      ),
      size: Feed.SIZE,
    }))
    async function load() {
      const id = ++requestId
      controller?.abort()
      controller = new AbortController()
      const requestedQuery = { ...query.value }
      loading.value = true
      try {
        const result = await contentApi.feed(requestedQuery, { signal: controller.signal })
        if (id !== requestId) return
        const pages = Math.max(1, Math.ceil(result.total / requestedQuery.size))
        if (requestedQuery.page > pages) {
          await filter({ page: pages })
          return
        }
        records.value = result.records
        total.value = result.total
        renderedPage.value = requestedQuery.page
        error.value = null
        hasLoaded.value = true
        revision.value++
      } catch (e) {
        if (id === requestId && !e.cancelled) {
          error.value = e
          hasLoaded.value = true
          revision.value++
        }
      } finally {
        if (id === requestId) loading.value = false
      }
    }
    function filter(next) {
      const merged = { ...query.value, page: 1, ...next }
      // 翻页后回到列表开头；切换筛选时筛选栏就在眼前，不滚动
      if (next?.page) {
        feedRoot.value?.scrollIntoView({
          behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
        })
      }
      return router.push({
        name: route.name,
        query: {
          ...(merged.category ? { category: merged.category } : {}),
          sort: merged.sort,
          page: String(merged.page),
        },
      })
    }
    function change({ id, field, value, delta }) {
      const post = records.value.find((p) => p.id === id)
      if (post) {
        post[field] = value
        const count = field === 'liked' ? 'likeCount' : 'favoriteCount'
        post[count] += delta
      }
    }
    watch(query, load, { immediate: true })
    return {
      scrollToTop,
      ...useI18n(),
      query,
      records,
      total,
      loading,
      hasLoaded,
      revision,
      renderedPage,
      feedRoot,
      error,
      load,
      filter,
      change,
      Categories,
      PostSort,
      homeArtwork,
      communitySparkle,
    }
  },
})
