import { defineComponent, ref, onMounted, onUnmounted } from 'vue'
import { CursorMotion } from '@/constants/visual'

// Canvas adaptation of the supplied Vue Bits GlowCursor reference.
// A bright nucleus, tapered fading tail and bounded stardust form a lightweight comet.
export default defineComponent({
  name: 'GlowCursor',
  setup() {
    const canvas = ref(null)
    let context,
      finePointer,
      reducedMotion,
      frame = 0,
      previous = 0,
      lastInput = 0,
      width = 0,
      height = 0,
      fade = 0,
      inside = false,
      initialized = false,
      primary,
      secondary,
      transparentPrimary,
      core,
      dustTime = 0
    const points = Array.from({ length: CursorMotion.POINTS }, () => ({ x: 0, y: 0 }))
    const dust = []
    const target = { x: 0, y: 0 }
    const enabled = () => finePointer.matches && !reducedMotion.matches && !document.hidden
    function clear() {
      context.clearRect(0, 0, width, height)
    }
    function paint() {
      clear()
      const head = points[0],
        tail = points.at(-1)
      const gradient = context.createLinearGradient(head.x, head.y, tail.x + 1, tail.y + 1)
      gradient.addColorStop(0, primary)
      gradient.addColorStop(1, secondary)
      context.lineCap = 'round'
      context.strokeStyle = gradient
      // Every layer tapers and fades; a uniform wide halo reads as a pasted ribbon.
      for (let index = points.length - 1; index > 0; index--) {
        const life = Math.pow(1 - index / points.length, CursorMotion.TAPER_POWER)
        const start = points[index - 1],
          end = points[index]
        if (Math.hypot(start.x - end.x, start.y - end.y) < 0.1) continue
        context.beginPath()
        context.moveTo(start.x, start.y)
        context.lineTo(end.x, end.y)
        context.lineWidth = CursorMotion.HALO_WIDTH * Math.sqrt(life)
        context.globalAlpha = fade * life * 0.12
        context.stroke()
        context.lineWidth = Math.max(0.15, CursorMotion.WIDTH * life)
        context.globalAlpha = fade * CursorMotion.OPACITY * life
        context.stroke()
      }
      for (const star of dust) {
        const life = Math.max(0, 1 - star.age / star.lifetime)
        context.globalAlpha = life * life * 0.6
        context.fillStyle = star.tint ? secondary : primary
        context.beginPath()
        context.arc(star.x, star.y, star.radius * life, 0, Math.PI * 2)
        context.fill()
      }
      const halo = context.createRadialGradient(
        head.x,
        head.y,
        0,
        head.x,
        head.y,
        CursorMotion.HEAD_HALO,
      )
      halo.addColorStop(0, core)
      halo.addColorStop(0.15, primary)
      halo.addColorStop(1, transparentPrimary)
      context.fillStyle = halo
      context.globalAlpha = fade * 0.8
      context.beginPath()
      context.arc(head.x, head.y, CursorMotion.HEAD_HALO, 0, Math.PI * 2)
      context.fill()
      context.fillStyle = core
      context.globalAlpha = fade
      context.beginPath()
      context.arc(head.x, head.y, CursorMotion.WIDTH * 0.65, 0, Math.PI * 2)
      context.fill()
    }
    function advanceDust(delta, dx, dy) {
      dustTime += delta
      const speed = delta ? Math.hypot(dx, dy) / delta : 0
      if (
        inside &&
        speed > CursorMotion.DUST_SPEED_THRESHOLD &&
        dustTime >= CursorMotion.DUST_INTERVAL &&
        dust.length < CursorMotion.MAX_DUST
      ) {
        dustTime = 0
        const side = (Math.random() - 0.5) * CursorMotion.DUST_SPREAD
        const length = Math.hypot(dx, dy)
        dust.push({
          x: points[1].x,
          y: points[1].y,
          vx: (-dx / delta) * 0.08 - (dy / length) * side,
          vy: (-dy / delta) * 0.08 + (dx / length) * side,
          age: 0,
          lifetime: CursorMotion.DUST_LIFETIME * (0.5 + Math.random() * 0.5),
          radius: CursorMotion.DUST_RADIUS * (0.5 + Math.random() * 0.5),
          tint: Math.random() > 0.5,
        })
      }
      for (let index = dust.length - 1; index >= 0; index--) {
        const star = dust[index]
        star.age += delta
        if (star.age >= star.lifetime) {
          dust.splice(index, 1)
          continue
        }
        star.x += star.vx * delta
        star.y += star.vy * delta
        const damping = Math.exp(-delta * 2)
        star.vx *= damping
        star.vy *= damping
      }
    }
    function render(now) {
      frame = 0
      if (!enabled()) {
        clear()
        return
      }
      const delta = Math.min((now - previous) / 1000, CursorMotion.MAX_DELTA)
      previous = now
      const headEase =
        1 - Math.pow(1 - CursorMotion.FOLLOW_SPEED, delta / CursorMotion.FRAME_SECONDS)
      const chainEase =
        1 - Math.pow(1 - CursorMotion.CHAIN_SPEED, delta / CursorMotion.FRAME_SECONDS)
      const oldX = points[0].x,
        oldY = points[0].y
      points[0].x += (target.x - points[0].x) * headEase
      points[0].y += (target.y - points[0].y) * headEase
      for (let index = 1; index < points.length; index++) {
        points[index].x += (points[index - 1].x - points[index].x) * chainEase
        points[index].y += (points[index - 1].y - points[index].y) * chainEase
      }
      advanceDust(delta, points[0].x - oldX, points[0].y - oldY)
      const active = inside && now - lastInput < CursorMotion.IDLE_MILLISECONDS
      const desired = active ? 1 : 0
      fade += (desired - fade) * (1 - Math.exp((-delta * 7) / CursorMotion.FADE_SECONDS))
      if (!active && fade < CursorMotion.MIN_FADE && !dust.length) {
        fade = 0
        initialized = false
        clear()
        return
      }
      paint()
      frame = requestAnimationFrame(render)
    }
    function wake() {
      if (frame || !enabled()) return
      previous = performance.now()
      frame = requestAnimationFrame(render)
    }
    function move(event) {
      if (event.pointerType === 'touch' || !enabled()) return
      target.x = event.clientX
      target.y = event.clientY
      if (!initialized) {
        for (const point of points) {
          point.x = target.x
          point.y = target.y
        }
        initialized = true
      }
      inside = true
      lastInput = performance.now()
      wake()
    }
    function leave(event) {
      if (event?.type === 'pointerout' && event.relatedTarget) return
      inside = false
      wake()
    }
    function resize() {
      width = innerWidth
      height = innerHeight
      const ratio = Math.min(devicePixelRatio || 1, CursorMotion.MAX_PIXEL_RATIO)
      canvas.value.width = width * ratio
      canvas.value.height = height * ratio
      context.setTransform(ratio, 0, 0, ratio, 0, 0)
      if (initialized) wake()
    }
    function suspend() {
      cancelAnimationFrame(frame)
      frame = 0
      fade = 0
      initialized = false
      dust.length = 0
      inside = false
      clear()
    }
    onMounted(() => {
      context = canvas.value.getContext('2d')
      if (!context) return
      const style = getComputedStyle(canvas.value)
      primary = style.getPropertyValue('--meng-cursor-color').trim()
      secondary = style.getPropertyValue('--meng-cursor-secondary').trim()
      transparentPrimary = style.getPropertyValue('--meng-cursor-clear').trim()
      core = style.getPropertyValue('--meng-surface').trim()
      finePointer = matchMedia('(hover: hover) and (pointer: fine)')
      reducedMotion = matchMedia('(prefers-reduced-motion: reduce)')
      window.addEventListener('pointermove', move, { passive: true })
      window.addEventListener('pointerout', leave)
      window.addEventListener('blur', leave)
      window.addEventListener('resize', resize)
      document.addEventListener('visibilitychange', suspend)
      reducedMotion.addEventListener('change', suspend)
      finePointer.addEventListener('change', suspend)
      resize()
    })
    onUnmounted(() => {
      cancelAnimationFrame(frame)
      window.removeEventListener('pointermove', move)
      window.removeEventListener('pointerout', leave)
      window.removeEventListener('blur', leave)
      window.removeEventListener('resize', resize)
      document.removeEventListener('visibilitychange', suspend)
      reducedMotion?.removeEventListener('change', suspend)
      finePointer?.removeEventListener('change', suspend)
    })
    return { canvas }
  },
})
