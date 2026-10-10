import { defineComponent, ref, onMounted, onUnmounted } from 'vue'
const layers = Object.freeze([
  { color: '--meng-tint', alpha: 0.78, baseline: 46, amplitude: 17, period: 720, speed: 0.65 },
  { color: '--meng-surface', alpha: 0.7, baseline: 78, amplitude: 12, period: 560, speed: -0.8 },
  { color: '--meng-bg', alpha: 1, baseline: 112, amplitude: 9, period: 640, speed: 0.5 },
])
const sampleStep = 8
export default defineComponent({
  name: 'HeroWaves',
  setup() {
    const canvas = ref(null)
    let context, resizeObserver, intersectionObserver, themeObserver, motion
    let width = 0,
      height = 0,
      palette = [],
      frame = 0,
      previousTime = null,
      elapsed = 0
    let visible = false,
      disposed = false
    function readPalette() {
      const style = getComputedStyle(document.documentElement)
      palette = layers.map((layer) => style.getPropertyValue(layer.color).trim())
      draw()
    }
    function draw() {
      if (!context || !width || !height) return
      context.clearRect(0, 0, width, height)
      layers.forEach((layer, index) => {
        context.globalAlpha = layer.alpha
        context.fillStyle = palette[index]
        context.beginPath()
        context.moveTo(0, height)
        const phase = elapsed * layer.speed
        const amplitude = layer.amplitude * (1 + Math.sin(elapsed * 0.36 + index) * 0.15)
        const swell = Math.sin(elapsed * 0.55 + index * 1.4) * 3
        for (let x = 0; x < width + sampleStep; x += sampleStep) {
          const angle = (x / layer.period) * Math.PI * 2
          // Opposing harmonics change the crest shape as well as moving it along the shoreline.
          const y =
            layer.baseline +
            swell +
            amplitude *
              (Math.sin(angle + phase + index) + 0.28 * Math.sin(angle * 1.7 - phase * 0.7))
          context.lineTo(x, y)
        }
        context.lineTo(width, height)
        context.closePath()
        context.fill()
      })
      context.globalAlpha = 1
    }
    function tick(time) {
      frame = 0
      if (previousTime != null) elapsed += Math.min((time - previousTime) / 1000, 0.05)
      previousTime = time
      draw()
      frame = requestAnimationFrame(tick)
    }
    function updatePlayback() {
      if (disposed) return
      const running = visible && !document.hidden && !motion.matches
      if (!running) {
        cancelAnimationFrame(frame)
        frame = 0
        previousTime = null
        draw()
      } else if (!frame) frame = requestAnimationFrame(tick)
    }
    function resize() {
      if (!canvas.value || disposed) return
      const rect = canvas.value.getBoundingClientRect()
      width = rect.width
      height = rect.height
      const ratio = Math.min(devicePixelRatio || 1, 2)
      canvas.value.width = Math.round(width * ratio)
      canvas.value.height = Math.round(height * ratio)
      context.setTransform(ratio, 0, 0, ratio, 0, 0)
      draw()
    }
    onMounted(() => {
      context = canvas.value.getContext('2d')
      if (!context) return
      readPalette()
      resizeObserver = new ResizeObserver(resize)
      resizeObserver.observe(canvas.value)
      intersectionObserver = new IntersectionObserver(([entry]) => {
        visible = entry.isIntersecting
        updatePlayback()
      })
      intersectionObserver.observe(canvas.value)
      themeObserver = new MutationObserver(readPalette)
      themeObserver.observe(document.documentElement, {
        attributes: true,
        attributeFilter: ['class'],
      })
      motion = matchMedia('(prefers-reduced-motion: reduce)')
      motion.addEventListener('change', updatePlayback)
      document.addEventListener('visibilitychange', updatePlayback)
    })
    onUnmounted(() => {
      disposed = true
      cancelAnimationFrame(frame)
      resizeObserver?.disconnect()
      intersectionObserver?.disconnect()
      themeObserver?.disconnect()
      motion?.removeEventListener('change', updatePlayback)
      document.removeEventListener('visibilitychange', updatePlayback)
    })
    return { canvas }
  },
})
