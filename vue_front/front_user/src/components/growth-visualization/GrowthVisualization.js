import { computed, defineComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { IconChartDonut, IconChartLine, IconCalendarMonth, IconX } from '@tabler/icons-vue'
import { GrowthCenter } from '@/constants/growth'
import { CreatorChartType } from '@/constants/content'
import { communitySparkle, detailBubble, detailFlower } from '@/assets/images'
import CreatorChart from '@/components/creator-chart/CreatorChart.vue'
import PageState from '@/components/page-state/PageState.vue'

export default defineComponent({
  name: 'GrowthVisualization',
  components: { CreatorChart, PageState, IconChartDonut, IconChartLine, IconCalendarMonth, IconX },
  props: {
    open: Boolean,
    days: { type: Number, required: true },
    stats: { type: Object, required: true },
  },
  emits: ['update:open', 'update:days', 'retry'],
  setup(props) {
    const total = computed(
      () => props.stats.data?.days.reduce((sum, row) => sum + row.amount, 0) ?? 0,
    )
    return {
      ...useI18n(),
      GrowthCenter,
      CreatorChartType,
      communitySparkle,
      detailBubble,
      detailFlower,
      total,
      period: computed(() => {
        const rows = props.stats.data?.days ?? []
        return rows.length
          ? rows[0].date.replaceAll('-', '/') + ' — ' + rows.at(-1).date.replaceAll('-', '/')
          : ''
      }),
    }
  },
})
