import { computed, defineComponent, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { IconCheck, IconCoin, IconMessageDots, IconPencil } from '@tabler/icons-vue'
import { growthApi } from '@/api/growth'
import { useRequestResource } from '@/composables/useRequestResource'
import { Points } from '@/constants/growth'
import { accountDate } from '@/utils/accountDate'
import DialogShell from '@/components/dialog-shell/DialogShell.vue'
import FeedPagination from '@/components/feed-pagination/FeedPagination.vue'
import PageState from '@/components/page-state/PageState.vue'
// 每种来源的颜色、图标，和每条规则的文案
const SourceTones = { CHECK_IN: 'mint', POST: 'blue', COMMENT: 'violet' }
const SourceIcons = { CHECK_IN: IconCheck, POST: IconPencil, COMMENT: IconMessageDots }
const RuleKeys = {
  CHECK_IN: 'account.ruleDaily',
  POST: 'account.rulePost',
  COMMENT: 'account.ruleComment',
}
// 柱子按顺序轮着用的颜色
const BarTones = ['mint', 'gold', 'pink', 'violet', 'blue']
const weekday = new Intl.DateTimeFormat('zh-CN', { weekday: 'narrow' })
// 积分弹窗：余额与获得规则、近几天每天获得的积分、来源占比、分页明细
export default defineComponent({
  name: 'PointsDialog',
  components: { DialogShell, FeedPagination, PageState },
  emits: ['close'],
  setup() {
    const { t } = useI18n()
    const days = ref(Points.DAYS[0]),
      source = ref(''),
      page = ref(1)
    const { state: points, load: loadPoints } = useRequestResource(
      (signal) => growthApi.points({ signal }),
      null,
    )
    const { state: stats, load: loadStats } = useRequestResource(
      (signal) => growthApi.pointStats(days.value, { signal }),
      null,
    )
    const { state: records, load: loadRecords } = useRequestResource(
      (signal) =>
        growthApi.pointRecords(
          { source: source.value || undefined, page: page.value, size: Points.SIZE },
          { signal },
        ),
      { records: [], total: 0 },
    )
    const shownDays = computed(() => stats.data?.days ?? [])
    const shownSources = computed(() => stats.data?.sources ?? [])
    const today = computed(() => shownDays.value.at(-1)?.amount ?? 0)
    const week = computed(() =>
      shownDays.value.slice(-7).reduce((sum, row) => sum + row.amount, 0),
    )
    const peak = computed(() => Math.max(1, ...shownDays.value.map((row) => row.amount)))
    const totalPages = computed(() => Math.ceil(records.data.total / Points.SIZE))
    const sourceFilters = [
      { key: '', label: 'account.pointsAll', tone: 'gold' },
      ...Object.keys(SourceTones).map((key) => ({
        key,
        label: 'account.sources.' + key,
        tone: SourceTones[key],
      })),
    ]
    function barLabel(row, index) {
      const [year, month, day] = row.date.split('-').map(Number)
      if (shownDays.value.length <= 7) return weekday.format(new Date(year, month - 1, day))
      return index % 5 === 0 || index === shownDays.value.length - 1 ? month + '/' + day : ''
    }
    function pickSource(value) {
      if (source.value === value) return
      source.value = value
      page.value = 1
    }
    function reload() {
      void loadPoints()
      void loadStats()
      void loadRecords()
    }
    watch(days, () => loadStats())
    watch([source, page], () => loadRecords())
    onMounted(reload)
    return {
      t,
      Points,
      IconCoin,
      points,
      stats,
      records,
      days,
      source,
      page,
      shownDays,
      shownSources,
      today,
      week,
      totalPages,
      sourceFilters,
      ruleKeys: RuleKeys,
      reload,
      loadStats,
      loadRecords,
      barLabel,
      pickSource,
      signed: (value) => (value > 0 ? '+' + value : String(value)),
      sourceTone: (value) => SourceTones[value] || 'gold',
      sourceIcon: (value) => SourceIcons[value] || IconCoin,
      barTone: (index) => BarTones[index % BarTones.length],
      barHeight: (amount) => Math.round((amount * 100) / peak.value),
      date: (value) => accountDate(value, t, true),
    }
  },
})
