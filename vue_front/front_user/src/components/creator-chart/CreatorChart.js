import { computed, defineComponent, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Creator, CreatorChartType } from '@/constants/content'
import PageState from '@/components/page-state/PageState.vue'
import { ExpSources } from '@/constants/growth'
export default defineComponent({
  name: 'CreatorChart',
  components: { PageState },
  props: {
    data: { type: Array, required: true },
    type: { type: String, required: true },
    days: { type: Number, default: 7 },
  },
  emits: ['open'],
  setup(props) {
    const { t } = useI18n(),
      host = ref(null),
      failed = ref(false)
    const keys = ['views', 'likes', 'comments', 'favorites']
    const enabled = ref(new Set(Creator.METRICS))
    const legend = computed(() =>
      Creator.METRICS.map((field, i) => ({
        field,
        key: keys[i],
        active: enabled.value.has(field),
      })),
    )
    let chart,
      observer,
      motion,
      disposed = false
    function colors() {
      const css = getComputedStyle(document.documentElement)
      const token = (name) => css.getPropertyValue('--meng-' + name).trim()
      return {
        text: token('muted'),
        grid: token('tint'),
        base: token('bg'),
        primary: token('primary'),
        lines: ['creator-green', 'creator-pink', 'creator-purple', 'creator-yellow'].map(token),
        bars: ['creator-yellow', 'creator-pink', 'category-anime', 'border', 'border'].map(token),
        sources: ['category-anime', 'category-daily', 'category-game-dot'].map(token),
      }
    }
    function update(reveal = false) {
      if (!chart || disposed) return
      const palette = colors()
      const common = {
        animation: !motion.matches,
        animationDuration: 350,
        animationDurationUpdate: 250,
        textStyle: { fontFamily: 'Nunito, Noto Sans SC, sans-serif' },
      }
      if (props.type === CreatorChartType.EXP_SOURCES) {
        chart.setOption(
          {
            ...common,
            color: palette.sources,
            tooltip: {
              trigger: 'item',
              renderMode: 'richText',
              confine: true,
              formatter: '{b}: {c} ({d}%)',
            },
            legend: { bottom: 0, textStyle: { color: palette.text } },
            series: [
              {
                type: 'pie',
                radius: ['38%', '66%'],
                center: ['50%', '44%'],
                label: { show: false },
                data: ExpSources.map((source) => ({
                  name: t('growth.source.' + source),
                  value: props.data.find((row) => row.source === source)?.amount ?? 0,
                })),
              },
            ],
          },
          { notMerge: true },
        )
        return
      }
      if (props.type === CreatorChartType.EXP_TREND) {
        if (reveal) chart.clear()
        chart.setOption(
          {
            ...common,
            animationDuration: 1000,
            animationEasing: 'cubicInOut',
            grid: { left: 38, right: 14, top: 22, bottom: 34 },
            tooltip: { trigger: 'axis', renderMode: 'richText', confine: true },
            xAxis: {
              type: 'category',
              boundaryGap: false,
              data: props.data.map((row) => row.date.slice(5).replace('-', '/')),
              axisTick: { show: false },
              axisLine: { show: false },
              axisLabel: { color: palette.text, hideOverlap: true },
            },
            yAxis: {
              type: 'value',
              min: 0,
              minInterval: 1,
              axisLabel: { color: palette.text },
              splitLine: { lineStyle: { color: palette.grid, type: 'dashed' } },
            },
            series: [
              {
                name: t('growth.chartTrend'),
                type: 'line',
                clip: true,
                showSymbol: false,
                data: props.data.map((row) => row.amount),
                lineStyle: { width: 3, color: palette.lines[0] },
                itemStyle: { color: palette.lines[0] },
              },
            ],
          },
          { notMerge: true },
        )
        return
      }
      if (props.type === CreatorChartType.TREND) {
        // 重新进入折线系列时让 ECharts 的裁剪矩形从左向右展开，坐标轴保持静止。
        if (reveal) chart.clear()
        chart.setOption(
          {
            ...common,
            animationDuration: 1150,
            animationDurationUpdate: 420,
            animationEasing: 'cubicInOut',
            grid: { left: 8, right: 8, top: 12, bottom: 48 },
            tooltip: {
              trigger: 'axis',
              renderMode: 'richText',
              confine: true,
              formatter: (params) => {
                const row = props.data[params[0]?.dataIndex]
                return row
                  ? [
                      row.date,
                      ...Creator.METRICS.map(
                        (field, i) => t('creator.' + keys[i]) + ': ' + row[field],
                      ),
                    ].join('\n')
                  : ''
              },
            },
            xAxis: {
              type: 'category',
              boundaryGap: false,
              data: props.data.map((d) => d.date),
              axisLine: { show: false },
              axisTick: { show: false },
              axisLabel: {
                color: palette.text,
                fontWeight: 700,
                hideOverlap: true,
                margin: 14,
                formatter: (value, i) =>
                  value.slice(5).replace('-', '/') +
                  '\n' +
                  (props.data[i].postCount ? '{posted|' + t('creator.posted') + '}' : ''),
                rich: { posted: { color: palette.primary, lineHeight: 16 } },
              },
            },
            yAxis: {
              type: 'value',
              min: 0,
              minInterval: 1,
              splitNumber: 4,
              axisLabel: { show: false },
              splitLine: { lineStyle: { color: palette.grid, type: 'dashed' } },
            },
            series: Creator.METRICS.map((field, i) => ({
              name: t('creator.' + keys[i]),
              type: 'line',
              clip: true,
              animationDelay: i * 65,
              showSymbol: false,
              data: enabled.value.has(field) ? props.data.map((d) => d[field]) : [],
              lineStyle: { width: 3, color: palette.lines[i] },
              itemStyle: { color: palette.lines[i] },
            })),
          },
          { notMerge: true },
        )
      } else {
        chart.setOption(
          {
            ...common,
            grid: { left: 40, right: 0, top: 0, bottom: (5 - props.data.length) * 70 },
            xAxis: {
              type: 'value',
              show: false,
              max: Math.max(1, ...props.data.map((d) => d.hotScore)),
            },
            yAxis: {
              type: 'category',
              inverse: true,
              data: props.data.map((d) => d.id),
              show: false,
            },
            series: [
              {
                type: 'bar',
                barWidth: 10,
                showBackground: true,
                backgroundStyle: { color: palette.base, borderRadius: 5 },
                data: props.data.map((d, i) => ({
                  value: d.hotScore,
                  itemStyle: { color: palette.bars[i], borderRadius: 5 },
                })),
              },
            ],
          },
          { notMerge: true },
        )
      }
    }
    function toggle(field) {
      const next = new Set(enabled.value)
      if (next.has(field)) next.delete(field)
      else next.add(field)
      enabled.value = next
      update()
    }
    async function mount() {
      failed.value = false
      observer?.disconnect()
      motion?.removeEventListener('change', motionChanged)
      chart?.dispose()
      chart = undefined
      try {
        const { init } = await import('./chartRuntime')
        if (disposed) return
        chart = init(host.value, null, { renderer: 'svg' })
        motion = matchMedia('(prefers-reduced-motion: reduce)')
        motion.addEventListener('change', motionChanged)
        observer = new ResizeObserver(() => chart?.resize())
        observer.observe(host.value)
        update(true)
      } catch {
        if (!disposed) failed.value = true
      }
    }
    function motionChanged() {
      update(true)
    }
    watch(
      () => props.data,
      () => update(true),
    )
    onMounted(mount)
    onUnmounted(() => {
      disposed = true
      observer?.disconnect()
      motion?.removeEventListener('change', motionChanged)
      chart?.dispose()
    })
    return {
      t,
      host,
      legend,
      toggle,
      failed,
      mount,
      CreatorChartType,
      chartDescription: computed(() =>
        props.type === CreatorChartType.TREND
          ? t('creator.chartDescription', { days: props.days })
          : props.type === CreatorChartType.TOP
            ? t('creator.performance')
            : t('growth.chartDescription', { days: props.days }),
      ),
      formatNumber: (value) => Number(value).toLocaleString('zh-CN'),
    }
  },
})
