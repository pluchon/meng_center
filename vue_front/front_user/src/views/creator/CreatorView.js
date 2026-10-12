import { computed, defineComponent, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { IconPencil, IconEye, IconHeart, IconMessage, IconStar } from '@tabler/icons-vue'
import { contentApi } from '@/api/content'
import { Creator, CreatorTab, CreatorChartType } from '@/constants/content'
import { RouteName } from '@/router/names'
import { communitySparkle, detailBubble, detailFlower } from '@/assets/images'
import CommunityLayout from '@/layouts/community/CommunityLayout.vue'
import PageState from '@/components/page-state/PageState.vue'
import FeedPagination from '@/components/feed-pagination/FeedPagination.vue'
import CreatorEntry from '@/components/creator-entry/CreatorEntry.vue'
import CreatorChart from '@/components/creator-chart/CreatorChart.vue'
import ShapeWaves from '@/components/shape-waves/ShapeWaves.vue'
import { useCreatorResource } from './useCreatorResource'
import { notifyFailure } from '@/composables/useFailureNotice'
import { scrollToTop } from '@/composables/scrollToTop'
export default defineComponent({
  name: 'CreatorView',
  components: {
    CommunityLayout,
    PageState,
    FeedPagination,
    CreatorEntry,
    CreatorChart,
    ShapeWaves,
    IconPencil,
  },
  setup() {
    const { t } = useI18n(),
      route = useRoute(),
      router = useRouter(),
      days = ref(Creator.DAYS[0])
    const tab = computed(() =>
      route.query.tab === CreatorTab.DRAFTS ? CreatorTab.DRAFTS : CreatorTab.PUBLISHED,
    )
    const page = computed(() =>
      Number.isSafeInteger(Number(route.query.page)) && Number(route.query.page) > 0
        ? Number(route.query.page)
        : 1,
    )
    const overview = useCreatorResource((signal) => contentApi.creatorOverview({ signal }), null)
    const trend = useCreatorResource(
      (signal) => contentApi.creatorTrend(days.value, { signal }),
      [],
    )
    const top = useCreatorResource((signal) => contentApi.creatorTopPosts({ signal }), [])
    const published = useCreatorResource(
      (signal) => contentApi.mine({ page: page.value, size: Creator.SIZE }, { signal }),
      { records: [], total: 0 },
    )
    const drafts = useCreatorResource((signal) => contentApi.drafts({ signal }), [])
    const deleting = reactive(new Set())
    const panels = { overview, trend, top, published, drafts }
    const metrics = [
      { field: 'postCount', key: 'posts', icon: IconPencil },
      { field: 'viewCount', key: 'views', icon: IconEye },
      { field: 'likeCount', key: 'likes', icon: IconHeart },
      { field: 'commentCount', key: 'comments', icon: IconMessage },
      { field: 'favoriteCount', key: 'favorites', icon: IconStar },
    ]
    function change(next) {
      return router.push({
        name: RouteName.CREATOR,
        query: { tab: tab.value, page: String(page.value), ...next },
      })
    }
    function openPost(id) {
      return router.push({
        name: RouteName.POST_DETAIL,
        params: { id },
        query: { from: 'creator', tab: tab.value, page: String(page.value) },
      })
    }
    async function loadPublished() {
      if (await published.load()) {
        const last = Math.max(1, Math.ceil(published.state.data.total / Creator.SIZE))
        if (page.value > last) await change({ page: String(last) })
      }
    }
    async function remove(id, isDraft) {
      const key = (isDraft ? CreatorTab.DRAFTS : CreatorTab.PUBLISHED) + id
      if (deleting.has(key)) return
      try {
        await ElMessageBox.confirm(
          t(isDraft ? 'creator.deleteDraft' : 'creator.deletePost'),
          t('creator.confirmTitle'),
          {
            confirmButtonText: t('creator.confirm'),
            cancelButtonText: t('creator.cancel'),
            type: 'warning',
          },
        )
      } catch {
        return
      }
      deleting.add(key)
      try {
        if (isDraft) {
          await contentApi.deleteDraft(id)
          await drafts.load()
        } else {
          await contentApi.delete(id)
          await Promise.all([loadPublished(), overview.load(), trend.load(), top.load()])
        }
        ElMessage.success(t('creator.deleted'))
      } catch (error) {
        notifyFailure(error)
      } finally {
        deleting.delete(key)
      }
    }
    watch(
      [tab, page],
      () => {
        if (tab.value === CreatorTab.PUBLISHED) void loadPublished()
      },
      { immediate: true },
    )
    watch(days, trend.load)
    onMounted(() => {
      document.documentElement.classList.add('creator-scrollbar-hidden')
      void overview.load()
      void trend.load()
      void top.load()
      void drafts.load()
    })
    onUnmounted(() => document.documentElement.classList.remove('creator-scrollbar-hidden'))
    return {
      t,
      RouteName,
      Creator,
      CreatorTab,
      CreatorChartType,
      tab,
      page,
      days,
      deleting,
      metrics,
      change,
      remove,
      openPost,
      loadPublished,
      scrollToTop,
      overview: overview.state,
      trend: trend.state,
      top: top.state,
      published: published.state,
      drafts: drafts.state,
      retry: (name) => panels[name].load(),
      formatNumber: (value) => Number(value).toLocaleString('zh-CN'),
      hasTrend: computed(
        () =>
          trend.state.data.some(
            (day) => day.postCount || Creator.METRICS.some((field) => day[field]),
          ) || overview.state.data?.postCount > 0,
      ),
      totalPages: computed(() => Math.ceil(published.state.data.total / Creator.SIZE)),
      communitySparkle,
      detailBubble,
      detailFlower,
    }
  },
})
