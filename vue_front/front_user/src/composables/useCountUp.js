import { onUnmounted, reactive, watch } from 'vue'

// 数字从当前值（第一次是 0）滚到目标值；source 返回 { 字段: 数字 } 或 null
export function useCountUp(source, { duration = 900 } = {}) {
  const values = reactive({})
  let frame = 0
  function stop() {
    cancelAnimationFrame(frame)
  }
  function run(target) {
    stop()
    const keys = Object.keys(target)
    if (matchMedia('(prefers-reduced-motion: reduce)').matches) {
      keys.forEach((key) => (values[key] = Number(target[key]) || 0))
      return
    }
    const start = performance.now(),
      from = Object.fromEntries(keys.map((key) => [key, values[key] || 0]))
    function tick(now) {
      const progress = Math.min(1, (now - start) / duration)
      const eased = 1 - Math.pow(1 - progress, 3)
      keys.forEach((key) => {
        const to = Number(target[key]) || 0
        values[key] = Math.round(from[key] + (to - from[key]) * eased)
      })
      if (progress < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
  }
  watch(
    source,
    (target) => {
      if (target) run(target)
    },
    { immediate: true },
  )
  onUnmounted(stop)
  return values
}
