import { defineComponent, ref, onMounted, onUnmounted } from 'vue'
import { BorderGlowMotion } from '@/constants/visual'

// Adapted from the supplied Vue Bits reference, without changing the card layout.
export default defineComponent({
  name: 'BorderGlow',
  setup() {
    const glow = ref(null)
    let card,
      frame = 0,
      pointer,
      motion,
      fine
    function reset() {
      cancelAnimationFrame(frame)
      frame = 0
      pointer = null
      glow.value?.style.setProperty('--edge-intensity', '0')
    }
    function paint() {
      frame = 0
      if (!pointer || card.inert || motion.matches || !fine.matches) {
        reset()
        return
      }
      const rect = card.getBoundingClientRect()
      const x = pointer.x - rect.left,
        y = pointer.y - rect.top
      const distance = Math.max(0, Math.min(x, y, rect.width - x, rect.height - y))
      const intensity = Math.max(0, 1 - distance / BorderGlowMotion.EDGE_DISTANCE)
      glow.value.style.setProperty('--edge-x', x + 'px')
      glow.value.style.setProperty('--edge-y', y + 'px')
      glow.value.style.setProperty('--edge-intensity', String(intensity))
    }
    function move(event) {
      if (event.pointerType === 'touch') {
        reset()
        return
      }
      pointer = { x: event.clientX, y: event.clientY }
      if (!frame) frame = requestAnimationFrame(paint)
    }
    onMounted(() => {
      card = glow.value.parentElement
      motion = matchMedia('(prefers-reduced-motion: reduce)')
      fine = matchMedia('(hover: hover) and (pointer: fine)')
      card.addEventListener('pointermove', move, { passive: true })
      card.addEventListener('pointerleave', reset)
      window.addEventListener('blur', reset)
      motion.addEventListener('change', reset)
      fine.addEventListener('change', reset)
    })
    onUnmounted(() => {
      reset()
      card?.removeEventListener('pointermove', move)
      card?.removeEventListener('pointerleave', reset)
      window.removeEventListener('blur', reset)
      motion?.removeEventListener('change', reset)
      fine?.removeEventListener('change', reset)
    })
    return { glow }
  },
})
