import { defineComponent, ref, onMounted, onUnmounted } from 'vue'
import { ParticleMotion } from '@/constants/visual'
import {
  advanceReconnection,
  buildConnections,
  disperseCrowds,
  advanceDispersion,
} from './particle-network'
export default defineComponent({
  name: 'ParticleBackground',
  setup() {
    const canvas = ref(null)
    let context,
      points = [],
      width = 0,
      height = 0,
      frame,
      previous = 0,
      color,
      motion,
      pointer = null,
      networkTime = 0,
      links = [],
      linkOpacity = new Map()
    function draw(time = 0) {
      const delta = motion.matches
        ? 0
        : Math.min((time - previous) / 1000, ParticleMotion.MAX_DELTA)
      previous = time
      context.clearRect(0, 0, width, height)
      context.fillStyle = color
      context.strokeStyle = color
      for (const point of points) {
        advanceDispersion(point, delta)
        advanceReconnection(point, points, delta, crossesPointer)
        if (pointer && delta) {
          const dx = point.x - pointer.x,
            dy = point.y - pointer.y
          const distance = Math.hypot(dx, dy)
          if (distance < ParticleMotion.POINTER_RADIUS) {
            const force =
              (1 - distance / ParticleMotion.POINTER_RADIUS) * ParticleMotion.POINTER_FORCE * delta
            point.x += (distance ? dx / distance : 1) * force
            point.y += (distance ? dy / distance : 0) * force
          }
        }
        point.x += point.vx * delta
        point.y += point.vy * delta
        if (point.x < 0 || point.x > width) {
          point.vx *= -1
          point.x = Math.max(0, Math.min(width, point.x))
        }
        if (point.y < 0 || point.y > height) {
          point.vy *= -1
          point.y = Math.max(0, Math.min(height, point.y))
        }
        context.globalAlpha = ParticleMotion.POINT_OPACITY
        context.beginPath()
        context.arc(point.x, point.y, ParticleMotion.RADIUS, 0, Math.PI * 2)
        context.fill()
      }
      networkTime -= delta
      if (networkTime <= 0) {
        const network = buildConnections(points, crossesPointer)
        links = network.links
        disperseCrowds(points, network.components, delta)
        points.forEach((point, index) => {
          const neighbors = network.neighbors[index]
          const gained = [...neighbors].some(
            (other) => !point.neighbors.has(other) && !point.excluded.has(other),
          )
          const lost = [...point.neighbors].filter((other) => !neighbors.has(other))
          if (gained) {
            point.reconnectTime = 0
            point.excluded.clear()
          }
          if (lost.length && !point.disperseTime) {
            point.reconnectTime = ParticleMotion.RECONNECT_SECONDS
            lost.forEach((other) => point.excluded.add(other))
          }
          point.neighbors = neighbors
        })
        const active = new Set(links.map(({ i, j }) => i * ParticleMotion.MAX_POINTS + j))
        for (const key of linkOpacity.keys()) if (!active.has(key)) linkOpacity.delete(key)
        networkTime = ParticleMotion.NETWORK_INTERVAL
      }
      for (const { i, j } of links) {
        if (crossesPointer(points[i], points[j])) continue
        const key = i * ParticleMotion.MAX_POINTS + j
        const distance = Math.hypot(points[i].x - points[j].x, points[i].y - points[j].y)
        const target =
          Math.max(0, 1 - distance / ParticleMotion.LINK_DISTANCE) * ParticleMotion.LINE_OPACITY
        const opacity = motion.matches
          ? target
          : (linkOpacity.get(key) ?? 0) +
            (target - (linkOpacity.get(key) ?? 0)) *
              (1 - Math.exp(-ParticleMotion.LINK_EASE * delta))
        linkOpacity.set(key, opacity)
        context.globalAlpha = opacity
        context.beginPath()
        context.moveTo(points[i].x, points[i].y)
        context.lineTo(points[j].x, points[j].y)
        context.stroke()
      }
      if (!motion.matches && !document.hidden) frame = requestAnimationFrame(draw)
    }
    function crossesPointer(start, end) {
      if (!pointer || motion.matches) return false
      const dx = end.x - start.x,
        dy = end.y - start.y
      const lengthSquared = dx * dx + dy * dy
      const position = lengthSquared
        ? Math.max(
            0,
            Math.min(1, ((pointer.x - start.x) * dx + (pointer.y - start.y) * dy) / lengthSquared),
          )
        : 0
      return (
        Math.hypot(start.x + position * dx - pointer.x, start.y + position * dy - pointer.y) <
        ParticleMotion.BREAK_RADIUS
      )
    }
    function movePointer(event) {
      if (
        event.pointerType === 'touch' ||
        event.target.closest?.('.auth-card, .legal-card, .reset-card')
      ) {
        pointer = null
        return
      }
      pointer = { x: event.clientX, y: event.clientY }
    }
    function clearPointer(event) {
      if (event?.type === 'pointerout' && event.relatedTarget) return
      pointer = null
    }
    function restart() {
      cancelAnimationFrame(frame)
      previous = performance.now()
      networkTime = 0
      if (!document.hidden) draw(previous)
    }
    function resize() {
      width = window.innerWidth
      height = window.innerHeight
      const ratio = Math.min(window.devicePixelRatio || 1, ParticleMotion.MAX_PIXEL_RATIO)
      canvas.value.width = width * ratio
      canvas.value.height = height * ratio
      context.setTransform(ratio, 0, 0, ratio, 0, 0)
      networkTime = 0
      linkOpacity.clear()
      const count = Math.max(
        ParticleMotion.MIN_POINTS,
        Math.min(
          ParticleMotion.MAX_POINTS,
          Math.round((width * height) / ParticleMotion.AREA_PER_POINT),
        ),
      )
      points = Array.from({ length: count }, () => ({
        x: Math.random() * width,
        y: Math.random() * height,
        vx: (Math.random() - 0.5) * ParticleMotion.SPEED,
        vy: (Math.random() - 0.5) * ParticleMotion.SPEED,
        neighbors: new Set(),
        excluded: new Set(),
        reconnectTime: 0,
        disperseTime: 0,
        spreadX: 0,
        spreadY: 0,
      }))
      restart()
    }
    onMounted(() => {
      context = canvas.value.getContext('2d')
      if (!context) return
      color = getComputedStyle(canvas.value).getPropertyValue('--meng-primary').trim()
      motion = window.matchMedia('(prefers-reduced-motion: reduce)')
      window.addEventListener('resize', resize)
      window.addEventListener('pointermove', movePointer, { passive: true })
      window.addEventListener('pointerout', clearPointer)
      window.addEventListener('blur', clearPointer)
      document.addEventListener('visibilitychange', restart)
      motion.addEventListener('change', restart)
      resize()
    })
    onUnmounted(() => {
      cancelAnimationFrame(frame)
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
