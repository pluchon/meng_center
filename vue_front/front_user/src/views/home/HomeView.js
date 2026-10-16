import { scrollToTop } from '@/composables/scrollToTop'
import { defineComponent, ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { contentApi } from '@/api/content'
import { Categories, PostSort, FeedSorts, Feed, Hero } from '@/constants/content'
import { IconChevronLeft, IconChevronRight } from '@tabler/icons-vue'
import {
  homeArtwork,
  homeComicMood,
  homeComicLove,
  homeComicSparkle,
  homeComicPartners,
  communitySparkle,
} from '@/assets/images'
import HeroWaves from '@/components/hero-waves/HeroWaves.vue'
import GeometryLines from '@/components/geometry-lines/GeometryLines.vue'
import CommunityLayout from '@/layouts/community/CommunityLayout.vue'
import PostCard from '@/components/post-card/PostCard.vue'
import FeedFilters from '@/components/feed-filters/FeedFilters.vue'
import FeedPagination from '@/components/feed-pagination/FeedPagination.vue'
import PageState from '@/components/page-state/PageState.vue'
import RecommendedAuthors from '@/components/recommended-authors/RecommendedAuthors.vue'
const homeScrollbarClass = 'home-scrollbar-hidden'
// 首页展示图，按这个顺序轮播，默认显示第一张。cover 的整幅铺满并带欢迎卡片、气泡和海浪；
// 其余是自带文字的漫画海报，完整显示，上面不叠任何东西
const slides = Object.freeze([
  { key: 'cirno', src: homeArtwork, cover: true, alt: 'home.brand' },
  { key: 'mood', src: homeComicMood, alt: 'home.posters.mood' },
  { key: 'love', src: homeComicLove, alt: 'home.posters.love' },
  { key: 'sparkle', src: homeComicSparkle, alt: 'home.posters.sparkle' },
  { key: 'partners', src: homeComicPartners, alt: 'home.posters.partners' },
])

export default defineComponent({
  name: 'HomeView',
  components: {
    IconChevronLeft,
    IconChevronRight,
    HeroWaves,
    GeometryLines,
    CommunityLayout,
    PostCard,
    FeedPagination,
    FeedFilters,
    PageState,
    RecommendedAuthors,
  },
  setup() {
    const current = ref(0)
    let timer,
      hovering = false
    const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)').matches
    // 自动轮播：鼠标停在上面、键盘聚焦在控制条上、页面在后台时都不翻；减少动态效果时不自动翻
    function tick() {
      clearInterval(timer)
      timer = undefined
      if (reduceMotion || hovering || document.hidden) return
      timer = setInterval(() => {
        current.value = (current.value + 1) % slides.length
      }, Hero.INTERVAL)
    }
    function go(index) {
      current.value = (index + slides.length) % slides.length
      tick()
    }
    function pause() {
      hovering = true
      tick()
    }
    function resume() {
      hovering = false
      tick()
    }
    onMounted(() => {
      document.documentElement.classList.add(homeScrollbarClass)
      document.addEventListener('visibilitychange', tick)
      tick()
    })
    onUnmounted(() => {
      document.documentElement.classList.remove(homeScrollbarClass)
      document.removeEventListener('visibilitychange', tick)
      clearInterval(timer)
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
      sort: FeedSorts.includes(route.query.sort) ? route.query.sort : PostSort.RECOMMEND,
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
      slides,
      current,
      go,
      pause,
      resume,
      communitySparkle,
    }
  },
})
