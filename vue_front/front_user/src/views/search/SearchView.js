import { scrollToTop } from '@/composables/scrollToTop'
import { computed, defineComponent, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { contentApi } from '@/api/content'
import { Categories, PostSort, Search } from '@/constants/content'
import { communitySparkle, detailBubble } from '@/assets/images'
import CommunityLayout from '@/layouts/community/CommunityLayout.vue'
import FeedFilters from '@/components/feed-filters/FeedFilters.vue'
import PostCard from '@/components/post-card/PostCard.vue'
import FeedPagination from '@/components/feed-pagination/FeedPagination.vue'
import PageState from '@/components/page-state/PageState.vue'
import Aurora from '@/components/aurora/AuroraBackground.vue'
export default defineComponent({
  name: 'SearchView',
  components: { CommunityLayout, FeedFilters, PostCard, FeedPagination, PageState, Aurora },
  setup() {
    onMounted(() => document.documentElement.classList.add('search-scrollbar-hidden'))
    onUnmounted(() => document.documentElement.classList.remove('search-scrollbar-hidden'))
    const route = useRoute(),
      router = useRouter(),
      records = ref([]),
      total = ref(0),
      loading = ref(false),
      error = ref(null)
    const query = computed(() => ({
      q: typeof route.query.q === 'string' ? route.query.q.trim() : '',
      category: Categories.includes(route.query.category) ? route.query.category : undefined,
      sort: Object.values(PostSort).includes(route.query.sort) ? route.query.sort : PostSort.HOT,
      page: Math.min(
        Search.WINDOW / Search.SIZE,
        Math.max(1, Number.isSafeInteger(Number(route.query.page)) ? Number(route.query.page) : 1),
      ),
    }))
    let controller,
      generation = 0
    async function load() {
      const current = ++generation
      controller?.abort()
      controller = new AbortController()
      error.value = null
      if (!query.value.q) {
        records.value = []
        total.value = 0
        loading.value = false
        return
      }
      loading.value = true
      try {
        const result = await contentApi.search(query.value, { signal: controller.signal })
        if (current !== generation) return
        records.value = result.records
        total.value = result.total
      } catch (e) {
        if (current === generation && !e.cancelled) error.value = e
      } finally {
        if (current === generation) loading.value = false
      }
    }
    function filter(next) {
      const merged = { ...query.value, page: 1, ...next }
      scrollToTop()
      return router.push({
        name: route.name,
        query: {
          q: merged.q,
          ...(merged.category ? { category: merged.category } : {}),
          sort: merged.sort,
          page: String(merged.page),
        },
      })
    }
    function change({ id, field, value, delta }) {
      const post = records.value.find((item) => item.id === id)
      if (post) {
        post[field] = value
        post[field === 'liked' ? 'likeCount' : 'favoriteCount'] += delta
      }
    }
    watch(query, load, { immediate: true })
    onUnmounted(() => {
      generation++
      controller?.abort()
    })
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
      totalPages: computed(() => Math.ceil(Math.min(total.value, Search.WINDOW) / Search.SIZE)),
      communitySparkle,
      detailBubble,
    }
  },
})
