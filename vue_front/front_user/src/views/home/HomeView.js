import { scrollToTop } from '@/composables/scrollToTop'
import { defineComponent, ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { contentApi } from '@/api/content'
import { Categories, PostSort, Feed } from '@/constants/content'
import { homeArtwork } from '@/assets/images'
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
    onUnmounted(() => document.documentElement.classList.remove(homeScrollbarClass))
    const route = useRoute(),
      router = useRouter(),
      records = ref([]),
      total = ref(0),
      loading = ref(false),
      error = ref(null)
    let requestId = 0
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
      loading.value = true
      error.value = null
      try {
        const result = await contentApi.feed(query.value)
        if (id !== requestId) return
        records.value = result.records
        total.value = result.total
        const pages = Math.max(1, Math.ceil(result.total / query.value.size))
        if (query.value.page > pages) await filter({ page: pages })
      } catch (e) {
        if (id === requestId) error.value = e
      } finally {
        if (id === requestId) loading.value = false
      }
    }
    function filter(next) {
      const merged = { ...query.value, page: 1, ...next }
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
      error,
      load,
      filter,
      change,
      Categories,
      PostSort,
      homeArtwork,
    }
  },
})
