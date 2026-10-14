import { defineComponent, ref, onMounted, onUnmounted } from 'vue'
import { createGeometryField } from './geometryLinesRuntime'
// 图形移动很慢，30 帧足够，省下一半的绘制
const FRAME_INTERVAL = 1000 / 30
export default defineComponent({
  name: 'GeometryLines',
  setup() {
    const canvas = ref(null)
    let field, resizeObserver, motion
    let frame = 0,
      previousTime = null,
      disposed = false
    function tick(time) {
      frame = requestAnimationFrame(tick)
      if (previousTime == null) previousTime = time
      const elapsed = time - previousTime
      if (elapsed < FRAME_INTERVAL) return
      previousTime = time
      field.step(Math.min(elapsed / 1000, 0.1))
      field.draw()
    }
    function updatePlayback() {
      if (disposed) return
      const running = !document.hidden && !motion.matches
      if (!running) {
        cancelAnimationFrame(frame)
        frame = 0
        previousTime = null
        field.draw()
      } else if (!frame) frame = requestAnimationFrame(tick)
    }
    onMounted(() => {
      if (!canvas.value.getContext('2d')) return
      field = createGeometryField(canvas.value)
      motion = matchMedia('(prefers-reduced-motion: reduce)')
      resizeObserver = new ResizeObserver(() => field.resize())
      resizeObserver.observe(canvas.value)
      motion.addEventListener('change', updatePlayback)
      document.addEventListener('visibilitychange', updatePlayback)
      updatePlayback()
    })
    onUnmounted(() => {
      disposed = true
      cancelAnimationFrame(frame)
      resizeObserver?.disconnect()
      motion?.removeEventListener('change', updatePlayback)
      document.removeEventListener('visibilitychange', updatePlayback)
    })
    return { canvas }
  },
})
