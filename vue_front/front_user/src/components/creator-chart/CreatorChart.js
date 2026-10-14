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
        ink: token('text'),
        card: token('creator-plot'),
        grid: token('tint'),
        base: token('bg'),
        lines: ['creator-green', 'creator-pink', 'creator-purple', 'creator-yellow'].map(token),
        bars: ['creator-yellow', 'creator-pink', 'category-anime', 'creator-green', 'creator-purple'].map(token),
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
        const compact = host.value.clientWidth < 340
        const rows = ExpSources.map((source, index) => ({
          name: t('growth.source.' + source),
          value: props.data.find((row) => row.source === source)?.amount ?? 0,
          // 颜色跟来源绑定：某个来源为 0 时，其他来源的颜色不会跟着错位
          itemStyle: { color: palette.sources[index] },
        })).filter((row) => row.value > 0)
        const total = rows.reduce((sum, row) => sum + row.value, 0)
        chart.setOption(
          {
            ...common,
            animationDuration: 900,
            animationEasing: 'cubicOut',
            tooltip: {
              trigger: 'item',
              renderMode: 'richText',
              confine: true,
              formatter: '{b}: {c} ({d}%)',
            },
            legend: {
              bottom: 0,
              icon: 'circle',
              itemWidth: 12,
              itemHeight: 12,
              itemGap: 18,
              textStyle: { color: palette.text, fontWeight: 700 },
            },
            // 圆环正中间：总经验
            title: {
              text: String(total),
              subtext: t('growth.totalExp'),
              left: 'center',
              top: compact ? '35%' : '37%',
              itemGap: 2,
              textStyle: { color: palette.ink, fontSize: 24, fontWeight: 400, fontFamily: 'ZCOOL KuaiLe, sans-serif' },
              subtextStyle: { color: palette.text, fontSize: 11, fontWeight: 700 },
            },
            series: [
              {
                type: 'pie',
                radius: compact ? ['34%', '52%'] : ['38%', '58%'],
                center: ['50%', '44%'],
                padAngle: 3,
                itemStyle: { borderColor: palette.card, borderWidth: 4, borderRadius: 12 },
                emphasis: { scaleSize: 6 },
                animationType: 'expansion',
                avoidLabelOverlap: true,
                label: {
                  show: true,
                  position: 'outside',
                  alignTo: 'edge',
                  edgeDistance: 10,
                  bleedMargin: 4,
                  distanceToLabelLine: 4,
                  color: palette.text,
                  fontSize: 11,
                  lineHeight: 17,
                  formatter: '{b} +{c}\n{d}%',
                },
                labelLine: { show: true, length: 10, length2: 8 },
                data: rows,
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
            animationDuration: 1100,
            animationEasing: 'cubicOut',
            grid: { left: 38, right: 18, top: 24, bottom: 34 },
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
                smooth: 0.35,
                showSymbol: true,
                symbol: 'circle',
                symbolSize: props.data.length > 10 ? 7 : 11,
                data: props.data.map((row) => row.amount),
                lineStyle: { width: 4, color: palette.lines[0], cap: 'round', join: 'round' },
                // 圆点带白边，线下面一层由深到浅的薄荷渐变
                itemStyle: { color: palette.lines[0], borderColor: palette.card, borderWidth: 3 },
                emphasis: { scale: 1.5 },
                areaStyle: {
                  color: {
                    type: 'linear',
                    x: 0,
                    y: 0,
                    x2: 0,
                    y2: 1,
                    colorStops: [
                      { offset: 0, color: palette.lines[0] + '8c' },
                      { offset: 1, color: palette.lines[0] + '00' },
                    ],
                  },
                },
              },
            ],
          },
          { notMerge: true },
        )
        return
      }
      if (props.type === CreatorChartType.TREND) {
        // 折线进场由 CSS 从左向右展开，每次换数据都重播；ECharts 只负责图例开关时的过渡。
        if (reveal) replay()
        chart.setOption(
          {
            ...common,
            animationDuration: 0,
            animationDurationUpdate: 360,
            animationEasing: 'cubicInOut',
            grid: { left: 8, right: 8, top: 12, bottom: 34 },
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
                formatter: (value) => value.slice(5).replace('-', '/'),
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
            animationDuration: 900,
            animationEasing: 'cubicOut',
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
                animationDelay: (index) => index * 90,
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
    // 重新触发进场动画：先摘掉类、强制回流，再加回去
    function replay() {
      const el = host.value
      if (!el) return
      el.classList.remove('reveal')
      void el.offsetWidth
      el.classList.add('reveal')
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
        observer = new ResizeObserver(() => {
          chart?.resize()
          if (props.type === CreatorChartType.EXP_SOURCES) update()
        })
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
