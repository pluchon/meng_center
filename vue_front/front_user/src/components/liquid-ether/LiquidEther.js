import { defineComponent, ref, onMounted, onUnmounted } from 'vue'
import { createLiquidSimulation } from './liquidSimulation'
export default defineComponent({
  name: 'LiquidEther',
  setup() {
    const host = ref(null)
    let engine, observer, resizeFrame
    const motion = window.matchMedia('(prefers-reduced-motion: reduce)')
    function refresh() {
      if (motion.matches) {
        engine?.dispose()
        engine = null
        return
      }
      if (document.hidden) {
        engine?.pause()
        return
      }
      if (!engine) {
        try {
          engine = createLiquidSimulation(host.value)
        } catch {
          engine = null
        }
      }
      engine?.start()
    }
    onMounted(() => {
      refresh()
      observer = new ResizeObserver(() => {
        cancelAnimationFrame(resizeFrame)
        resizeFrame = requestAnimationFrame(() => engine?.resize())
      })
      observer.observe(host.value)
      document.addEventListener('visibilitychange', refresh)
      motion.addEventListener('change', refresh)
    })
    onUnmounted(() => {
      observer?.disconnect()
      cancelAnimationFrame(resizeFrame)
      document.removeEventListener('visibilitychange', refresh)
      motion.removeEventListener('change', refresh)
      engine?.dispose()
    })
    return { host }
  },
})
