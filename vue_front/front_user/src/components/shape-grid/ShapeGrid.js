import { defineComponent, onMounted, onUnmounted, ref, watch } from 'vue'
import { ShapeGridMotion } from '@/constants/visual'

// 按 Vue Bits ShapeGrid 的方格变体适配；动画按经过时间推进，避免高刷新率加速。
export default defineComponent({
  name: 'ShapeGrid',
  props: {
    speed: { type: Number, default: ShapeGridMotion.SPEED },
    squareSize: { type: Number, default: ShapeGridMotion.CELL_SIZE },
  },
  setup(props) {
    const canvas = ref(null)
    let context,
      motion,
      observer,
      frame,
      previous = 0,
      width = 0,
      height = 0
    let offset = 0,
      pointer = null,
      fill = 0,
      strokeColor,
      hoverColor
    const cellSize = () => Math.max(16, props.squareSize || ShapeGridMotion.CELL_SIZE)

    function paint() {
      const size = cellSize()
      context.clearRect(0, 0, width, height)
      if (pointer && fill > 0) {
        context.globalAlpha = fill
        context.fillStyle = hoverColor
        context.fillRect(
          Math.floor((pointer.x - offset) / size) * size + offset,
          Math.floor((pointer.y - offset) / size) * size + offset,
          size,
          size,
        )
        context.globalAlpha = 1
      }
      context.beginPath()
      for (let x = offset - size; x <= width; x += size) {
        context.moveTo(x, 0)
        context.lineTo(x, height)
      }
      for (let y = offset - size; y <= height; y += size) {
        context.moveTo(0, y)
        context.lineTo(width, y)
      }
      context.strokeStyle = strokeColor
      context.lineWidth = 1
      context.stroke()
    }

    function animate(time) {
      const elapsed = (time - previous) / 1000
      if (elapsed >= ShapeGridMotion.FRAME_SECONDS) {
        const delta = Math.min(elapsed, ShapeGridMotion.MAX_DELTA)
        previous = time
        const size = cellSize()
        offset =
          (offset - Math.max(0, props.speed) * ShapeGridMotion.BASE_FPS * delta + size) % size
        fill += ((pointer ? 1 : 0) - fill) * (1 - Math.exp(-8 * delta))
        paint()
      }
      frame = requestAnimationFrame(animate)
    }

    function restart() {
      cancelAnimationFrame(frame)
      if (!context || document.hidden) return
      previous = performance.now()
      if (motion.matches) {
        pointer = null
        fill = 0
      }
      paint()
      if (!motion.matches) frame = requestAnimationFrame(animate)
    }

    function resize() {
      const bounds = canvas.value.getBoundingClientRect()
      width = bounds.width
      height = bounds.height
      const ratio = Math.min(window.devicePixelRatio || 1, ShapeGridMotion.MAX_PIXEL_RATIO)
      canvas.value.width = Math.round(width * ratio)
      canvas.value.height = Math.round(height * ratio)
      context.setTransform(ratio, 0, 0, ratio, 0, 0)
      const style = getComputedStyle(canvas.value)
      strokeColor = style.getPropertyValue('--meng-companion-grid-line').trim()
      hoverColor = style.getPropertyValue('--meng-companion-grid-hover').trim()
      restart()
    }

    function movePointer(event) {
      if (motion.matches || event.pointerType !== 'mouse') return
      pointer = { x: event.clientX, y: event.clientY }
    }
    function clearPointer(event) {
      if (event?.type === 'pointerout' && event.relatedTarget) return
      pointer = null
    }

    onMounted(() => {
      context = canvas.value.getContext('2d')
      if (!context) return
      motion = window.matchMedia('(prefers-reduced-motion: reduce)')
      observer = new ResizeObserver(resize)
      observer.observe(canvas.value)
      window.addEventListener('resize', resize)
      window.addEventListener('pointermove', movePointer, { passive: true })
      window.addEventListener('pointerout', clearPointer)
      window.addEventListener('blur', clearPointer)
      document.addEventListener('visibilitychange', restart)
      motion.addEventListener('change', restart)
      resize()
    })
    watch(() => [props.speed, props.squareSize], restart)
    onUnmounted(() => {
      cancelAnimationFrame(frame)
      observer?.disconnect()
      window.removeEventListener('resize', resize)
      window.removeEventListener('pointermove', movePointer)
      window.removeEventListener('pointerout', clearPointer)
      window.removeEventListener('blur', clearPointer)
      document.removeEventListener('visibilitychange', restart)
      motion?.removeEventListener('change', restart)
    })
    return { canvas }
  },
})
