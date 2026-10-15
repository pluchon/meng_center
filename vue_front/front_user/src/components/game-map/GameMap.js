import { computed, defineComponent, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { HOUSES, ISLAND, PATHS, PLACES, SAND_LINE, SHOP, heightAt, slopeAt } from '@/game/terrain'

// 地图上的颜色；地形底图只画一次，角色箭头每帧重画
const MapColor = Object.freeze({
  sea: [191, 230, 242],
  sand: [244, 231, 200],
  low: [168, 221, 176],
  high: [110, 178, 132],
  rock: [196, 203, 210],
  path: '#EBD3AE',
  house: '#F2B3C6',
  shop: '#F6D77A',
  shrine: '#C3B4EC',
  lighthouse: '#FFFFFF',
  player: '#367F9A',
})
const MINI = 168
const FULL = 520
// 底图分辨率，放大时由画布缩放
const BASE = 260

function paintBase() {
  const canvas = document.createElement('canvas')
  canvas.width = BASE
  canvas.height = BASE
  const ctx = canvas.getContext('2d')
  const image = ctx.createImageData(BASE, BASE)
  const span = ISLAND.radius + 12
  for (let py = 0; py < BASE; py++)
    for (let px = 0; px < BASE; px++) {
      const x = (px / BASE) * 2 * span - span
      const z = span - (py / BASE) * 2 * span
      const h = heightAt(x, z)
      let c
      if (h < 0) c = MapColor.sea
      else if (h < SAND_LINE && Math.hypot(x, z) > 72) c = MapColor.sand
      else if (slopeAt(x, z) > 0.7 || h > 24) c = MapColor.rock
      else {
        const k = Math.min(1, h / 22)
        c = MapColor.low.map((v, i) => v + (MapColor.high[i] - v) * k)
      }
      image.data.set([c[0], c[1], c[2], 255], (py * BASE + px) * 4)
    }
  ctx.putImageData(image, 0, 0)
  const toPx = (x, z) => [((x + span) / (2 * span)) * BASE, ((span - z) / (2 * span)) * BASE]
  ctx.strokeStyle = MapColor.path
  ctx.lineWidth = 2.4
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  for (const path of PATHS) {
    ctx.beginPath()
    path.forEach(([x, z], i) => (i ? ctx.lineTo(...toPx(x, z)) : ctx.moveTo(...toPx(x, z))))
    ctx.stroke()
  }
  ctx.fillStyle = MapColor.house
  for (const h of HOUSES) {
    const [x, y] = toPx(h.x - h.w / 2, h.z + h.d / 2)
    ctx.fillRect(x, y, (h.w / (2 * span)) * BASE, (h.d / (2 * span)) * BASE)
  }
  return { canvas, span }
}

// 小地图：右下角一块，点一下或按 M 放大；北在上
export default defineComponent({
  name: 'GameMap',
  props: {
    open: { type: Boolean, default: false },
    shrine: { type: Object, required: true },
    // 返回 { x, z, heading }，每帧读一次
    locate: { type: Function, required: true },
  },
  emits: ['toggle'],
  setup(props) {
    const { t } = useI18n()
    const canvas = ref(null)
    const size = computed(() => (props.open ? FULL : MINI))
    const legend = [
      { key: 'you', color: MapColor.player },
      { key: 'shop', color: MapColor.shop },
      { key: 'shrine', color: MapColor.shrine },
      { key: 'lighthouse', color: MapColor.lighthouse },
      { key: 'houses', color: MapColor.house },
    ]
    let base = null
    let frame = 0

    function marker(ctx, px, py, color, r) {
      ctx.beginPath()
      ctx.arc(px, py, r, 0, Math.PI * 2)
      ctx.fillStyle = color
      ctx.fill()
      ctx.lineWidth = 2
      ctx.strokeStyle = MapColor.player
      ctx.stroke()
    }

    function draw() {
      frame = requestAnimationFrame(draw)
      const ctx = canvas.value?.getContext('2d')
      if (!ctx) return
      const n = size.value
      const scale = n / BASE
      const toPx = (x, z) => [((x + base.span) / (2 * base.span)) * n, ((base.span - z) / (2 * base.span)) * n]
      ctx.clearRect(0, 0, n, n)
      ctx.drawImage(base.canvas, 0, 0, n, n)
      const r = Math.max(4, 3 * scale)
      marker(ctx, ...toPx(SHOP.x, SHOP.z), MapColor.shop, r)
      marker(ctx, ...toPx(props.shrine.x, props.shrine.z), MapColor.shrine, r)
      marker(ctx, ...toPx(PLACES.lighthouse.x, PLACES.lighthouse.z), MapColor.lighthouse, r)
      const me = props.locate()
      if (!me) return
      const [px, py] = toPx(me.x, me.z)
      // 视野扇形和角色箭头；屏幕上 y 向下，所以 z 方向取反
      const a = Math.atan2(-Math.cos(me.heading), Math.sin(me.heading))
      ctx.beginPath()
      ctx.moveTo(px, py)
      ctx.arc(px, py, 10 * Math.max(1.6, scale), a - 0.55, a + 0.55)
      ctx.closePath()
      ctx.fillStyle = 'rgb(255 255 255 / 45%)'
      ctx.fill()
      const s = Math.max(6, 4 * scale)
      const ya = Math.atan2(-Math.cos(me.yaw), Math.sin(me.yaw))
      ctx.beginPath()
      ctx.moveTo(px + Math.cos(ya) * s, py + Math.sin(ya) * s)
      ctx.lineTo(px + Math.cos(ya + 2.5) * s * 0.8, py + Math.sin(ya + 2.5) * s * 0.8)
      ctx.lineTo(px + Math.cos(ya - 2.5) * s * 0.8, py + Math.sin(ya - 2.5) * s * 0.8)
      ctx.closePath()
      ctx.fillStyle = MapColor.player
      ctx.strokeStyle = '#FFFFFF'
      ctx.lineWidth = 2
      ctx.fill()
      ctx.stroke()
    }

    onMounted(() => {
      base = paintBase()
      draw()
    })
    onBeforeUnmount(() => cancelAnimationFrame(frame))
    return { t, canvas, size, legend }
  },
})
