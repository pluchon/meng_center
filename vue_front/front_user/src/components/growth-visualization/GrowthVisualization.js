import { computed, defineComponent, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { GrowthCenter } from '@/constants/growth'
import { CreatorChartType } from '@/constants/content'
import {
  communitySparkle,
  detailBubble,
  detailFlower,
  growthChartIcon,
  growthTrendIcon,
  growthCalendarIcon,
  messageCloseIcon,
} from '@/assets/images'
import CreatorChart from '@/components/creator-chart/CreatorChart.vue'
import PageState from '@/components/page-state/PageState.vue'

export default defineComponent({
  name: 'GrowthVisualization',
  components: { CreatorChart, PageState },
  props: {
    open: Boolean,
    days: { type: Number, required: true },
    stats: { type: Object, required: true },
  },
  emits: ['update:open', 'update:days', 'retry'],
  setup(props) {
    // 切换 7 天 / 30 天时新数据还没回来，先继续显示上一份，等新数据到了再换
    const shown = ref(props.stats.data)
    watch(
      () => props.stats.data,
      (data) => {
        if (data) shown.value = data
      },
    )
    const total = computed(() => shown.value?.days.reduce((sum, row) => sum + row.amount, 0) ?? 0)
    return {
      ...useI18n(),
      GrowthCenter,
      icons: {
        chart: growthChartIcon,
        trend: growthTrendIcon,
        calendar: growthCalendarIcon,
        close: messageCloseIcon,
      },
      CreatorChartType,
      communitySparkle,
      detailBubble,
      detailFlower,
      total,
      shown,
      // 正在换数据：旧图还在，但要让它淡一点并转圈
      switching: computed(() => Boolean(shown.value) && (!props.stats.data || props.stats.loading)),
      period: computed(() => {
        const rows = shown.value?.days ?? []
        return rows.length
          ? rows[0].date.replaceAll('-', '/') + ' — ' + rows.at(-1).date.replaceAll('-', '/')
          : ''
      }),
    }
  },
})
