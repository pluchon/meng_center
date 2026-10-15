import { computed, defineComponent, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  growthCalendarIcon,
  growthFlameIcon,
  growthFlagIcon,
  growthReceiptIcon,
  growthChartIcon,
  growthCheckIcon,
  growthClockIcon,
  editorMagicIcon,
  editorPencilIcon,
  creatorCommentsIcon,
} from '@/assets/images'
import { useCountUp } from '@/composables/useCountUp'
import { growthApi } from '@/api/growth'
import { useGrowthStore } from '@/stores/growth'
import { useUserStore } from '@/stores/user'
import { useRequestResource } from '@/composables/useRequestResource'
import { notifyFailure } from '@/composables/useFailureNotice'
import { RouteName } from '@/router/names'
import { ExpSource, ExpSources, SourceTones, GrowthCenter } from '@/constants/growth'
import CommunityLayout from '@/layouts/community/CommunityLayout.vue'
import PageState from '@/components/page-state/PageState.vue'
import GrowthPanel from '@/components/growth-panel/GrowthPanel.vue'
import GrowthLevel from '@/components/growth-level/GrowthLevel.vue'
import GrowthCalendar from '@/components/growth-calendar/GrowthCalendar.vue'
import GrowthTasks from '@/components/growth-tasks/GrowthTasks.vue'
import FeedPagination from '@/components/feed-pagination/FeedPagination.vue'
import GrowthVisualization from '@/components/growth-visualization/GrowthVisualization.vue'
import GradientWaves from '@/components/gradient-waves/GradientWaves.vue'

export default defineComponent({
  name: 'GrowthView',
  components: {
    CommunityLayout,
    PageState,
    GrowthPanel,
    GrowthLevel,
    GrowthCalendar,
    GrowthTasks,
    FeedPagination,
    GrowthVisualization,
    GradientWaves,
  },
  setup() {
    const { t } = useI18n(),
      growth = useGrowthStore(),
      user = useUserStore(),
      route = useRoute(),
      router = useRouter()
    const month = ref(''),
      signing = ref(false),
      visualOpen = ref(false),
      visualMounted = ref(false),
      days = ref(GrowthCenter.DAYS[0]),
      footer = ref(null)
    const months = new Map()
    let refreshCalendar = false
    const source = computed(() =>
      typeof route.query.source === 'string' ? route.query.source : '',
    )
    const page = computed(() =>
      Number.isSafeInteger(Number(route.query.page)) && Number(route.query.page) > 0
        ? Number(route.query.page)
        : 1,
    )
    const overview = useRequestResource((signal) => growthApi.overview({ signal }), null)
    const seven = useRequestResource((signal) => growthApi.stats(7, { signal }), null)
    const thirty = useRequestResource((signal) => growthApi.stats(30, { signal }), null)
    const records = useRequestResource(
      (signal) =>
        growthApi.records(
          { source: source.value || undefined, page: page.value, size: GrowthCenter.SIZE },
          { signal },
        ),
      { records: [], total: 0 },
    )
    const calendar = useRequestResource(async (signal) => {
      const requested = month.value
      const owner = user.current?.id
      if (refreshCalendar) {
        const current = await growthApi.checkIns(undefined, { signal })
        if (signal.aborted || owner !== user.current?.id) return current
        for (const [key, value] of months)
          months.set(key, { ...value, today: current.today, streak: current.streak })
        months.set(current.month, current)
        refreshCalendar = false
        if (!requested) return current
      }
      if (months.has(requested)) return months.get(requested)
      const result = await growthApi.checkIns(requested || undefined, { signal })
      if (!signal.aborted && owner === user.current?.id) months.set(result.month, result)
      return result
    }, null)
    // 连续签到和本月签到的数字，每次换月或签到后从旧值滚到新值
    const calendarCounts = useCountUp(() =>
      calendar.state.data
        ? { streak: calendar.state.data.streak ?? 0, count: calendar.state.data.count ?? 0 }
        : null,
    )
    async function loadCalendar() {
      if (await calendar.load()) month.value = calendar.state.data.month
    }
    function moveMonth(next) {
      month.value = next
      void loadCalendar()
    }
    function changeRecords(next) {
      return router.push({ name: RouteName.GROWTH, query: { ...route.query, ...next } })
    }
    async function loadRecords() {
      if (await records.load()) {
        const last = Math.max(1, Math.ceil(records.state.data.total / GrowthCenter.SIZE))
        if (page.value > last)
          await router.replace({
            name: RouteName.GROWTH,
            query: { ...route.query, page: String(last) },
          })
      }
    }
    async function refreshAfterCheckIn() {
      months.delete(growth.data?.today?.slice(0, 7))
      refreshCalendar = true
      await Promise.all([
        overview.load(),
        seven.load(),
        loadRecords(),
        thirty.state.data ? thirty.load() : Promise.resolve(),
        loadCalendar(),
      ])
    }
    async function checkIn() {
      if (signing.value) return
      signing.value = true
      const reward =
        calendar.state.data?.reward ??
        overview.state.data?.tasks.find((task) => task.source === ExpSource.CHECK_IN)?.reward
      try {
        const result = await growth.checkIn()
        if (!result) return
        ElMessage.success(t('growth.checkSuccess', { reward }))
        await refreshAfterCheckIn()
      } catch (error) {
        if (error.status === 409) {
          await refreshAfterCheckIn()
          ElMessage.info(t('growth.checkedIn'))
        } else notifyFailure(error)
      } finally {
        signing.value = false
      }
    }
    function toggleVisual() {
      visualOpen.value = !visualOpen.value
      if (visualOpen.value) {
        visualMounted.value = true
        if (days.value === 30 && !thirty.state.data) void thirty.load()
        if (days.value === 7 && !seven.state.data) void seven.load()
      }
    }
    const visibleStats = computed(() => (days.value === 7 ? seven : thirty))
    watch(days, () => {
      if (visualMounted.value && !visibleStats.value.state.data) void visibleStats.value.load()
    })
    watch([source, page], loadRecords, { immediate: true })
    watch(
      () => growth.data?.today,
      (next, previous) => {
        if (next && previous && next !== previous) {
          months.clear()
          refreshCalendar = true
          if (month.value === previous.slice(0, 7)) month.value = next.slice(0, 7)
          void loadCalendar()
          void overview.load()
          void seven.load()
          if (thirty.state.data) void thirty.load()
          void loadRecords()
        }
      },
    )
    watch(
      () => user.current?.id,
      (next, previous) => {
        if (next === previous) return
        months.clear()
        refreshCalendar = false
        for (const resource of [overview, seven, thirty, records, calendar]) resource.clear()
        month.value = ''
        if (next) {
          void overview.load()
          void seven.load()
          void loadCalendar()
          void loadRecords()
          if (visualMounted.value && days.value === 30) void thirty.load()
        }
      },
    )
    onMounted(() => {
      document.documentElement.classList.add('growth-scrollbar-hidden')
      void growth.load()
      void overview.load()
      void seven.load()
      void loadCalendar()
    })
    onUnmounted(() => document.documentElement.classList.remove('growth-scrollbar-hidden'))
    // 明细翻页也保持底部操作行在视口中的位置；来源筛选保持当前视口。
    let paging = false
    function nextPage(next) {
      paging = true
      return changeRecords({ page: String(next) })
    }
    watch(
      () => records.state.data,
      async (_, previous) => {
        if (!paging || !previous.records.length || !footer.value) return
        const top = footer.value.getBoundingClientRect().top
        await nextTick()
        if (route.name === RouteName.GROWTH && footer.value)
          window.scrollBy({
            top: footer.value.getBoundingClientRect().top - top,
            behavior: 'instant',
          })
        paging = false
      },
    )
    const stateOf = (resource) =>
      resource.error ? 'error' : resource.loading ? 'loading' : 'empty'
    function recordTime(value) {
      const date = value.slice(0, 10),
        today = calendar.state.data?.today ?? growth.data?.today
      const elapsed = Math.floor((Date.now() - Date.parse(value)) / 60000)
      if (date === today && elapsed >= 0 && elapsed < 60)
        return elapsed === 0 ? t('growth.justNow') : t('growth.minutesAgo', { count: elapsed })
      if (date === today && elapsed >= 60 && elapsed < 1440)
        return t('growth.hoursAgo', { count: Math.floor(elapsed / 60) })
      const yesterday = today ? new Date(today + 'T00:00:00Z') : null
      yesterday?.setUTCDate(yesterday.getUTCDate() - 1)
      return (
        (date === today
          ? t('growth.today')
          : date === yesterday?.toISOString().slice(0, 10)
            ? t('growth.yesterday')
            : date.slice(5).replace('-', '/')) +
        ' ' +
        value.slice(11, 16)
      )
    }
    return {
      t,
      growth,
      user,
      source,
      page,
      month,
      signing,
      visualOpen,
      visualMounted,
      days,
      footer,
      ExpSources,
      SourceTones,
      GrowthCenter,
      icons: {
        calendar: growthCalendarIcon,
        flame: growthFlameIcon,
        flag: growthFlagIcon,
        receipt: growthReceiptIcon,
        chart: growthChartIcon,
        check: growthCheckIcon,
        clock: growthClockIcon,
        magic: editorMagicIcon,
      },
      sourceIcons: {
        CHECK_IN: growthCalendarIcon,
        POST: editorPencilIcon,
        COMMENT: creatorCommentsIcon,
      },
      calendarCounts,
      overview: overview.state,
      calendar: calendar.state,
      seven: seven.state,
      records: records.state,
      visibleStats: computed(() => visibleStats.value.state),
      totalPages: computed(() => Math.ceil(records.state.data.total / GrowthCenter.SIZE)),
      completed: computed(
        () => overview.state.data?.tasks.filter((task) => task.count > 0).length ?? 0,
      ),
      checked: computed(
        () =>
          growth.data?.checkedIn ??
          calendar.state.data?.dates.includes(calendar.state.data.today) ??
          false,
      ),
      name: computed(() => user.current?.nickname || user.current?.username || ''),
      stateOf,
      recordTime,
      loadRecords,
      loadCalendar,
      moveMonth,
      changeRecords,
      nextPage,
      checkIn,
      toggleVisual,
      retryOverview: overview.load,
      retrySeven: seven.load,
      retryStats: () => visibleStats.value.load(),
      unavailable: () => ElMessage.info(t('home.unavailable')),
    }
  },
})
