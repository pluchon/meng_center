import { defineComponent, ref, onMounted, onUnmounted } from 'vue'
import { BorderGlowMotion } from '@/constants/visual'

// 卡片边缘是光源：光点落在离指针最近的那段边上，越靠近边越亮，光向两侧像雾一样散开。
export default defineComponent({
  name: 'BorderGlow',
  setup() {
    const glow = ref(null)
    let card,
      frame = 0,
      pointer,
      light,
      motion,
      fine
    function reset() {
      cancelAnimationFrame(frame)
      frame = 0
      pointer = null
      light = null
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
      const left = x,
        right = rect.width - x,
        top = y,
        bottom = rect.height - y
      const distance = Math.max(0, Math.min(left, right, top, bottom))
      // 把指针投到最近的边上，作为光源的位置
      const target = { x, y }
      if (distance === left) target.x = 0
      else if (distance === right) target.x = rect.width
      else if (distance === top) target.y = 0
      else target.y = rect.height
      // 光源缓缓跟过去，不跟手跳动
      light ??= { ...target }
      light.x += (target.x - light.x) * BorderGlowMotion.FOLLOW
      light.y += (target.y - light.y) * BorderGlowMotion.FOLLOW
      const near = Math.max(0, 1 - distance / BorderGlowMotion.EDGE_DISTANCE)
      const intensity = near * near * (3 - 2 * near)
      glow.value.style.setProperty('--edge-x', light.x + 'px')
      glow.value.style.setProperty('--edge-y', light.y + 'px')
      glow.value.style.setProperty('--edge-intensity', intensity.toFixed(3))
      if (
        Math.abs(target.x - light.x) > BorderGlowMotion.SETTLE ||
        Math.abs(target.y - light.y) > BorderGlowMotion.SETTLE
      )
        frame = requestAnimationFrame(paint)
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
