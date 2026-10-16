import { computed, defineComponent, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { HOUSES, PATHS, PLACES, SAND_LINE, SHOP, SKY_ISLANDS, SNOW_LINE, TOWERS, WORLD, heightAt, slopeAt, surfaceNear } from '@/game/terrain'

// 地图上的颜色；地形底图只画一次，角色箭头每帧重画
const MapColor = Object.freeze({
  deep: [150, 204, 228],
  shallow: [191, 232, 240],
  sand: [244, 231, 200],
  low: [172, 222, 172],
  high: [116, 176, 132],
  woods: [128, 186, 140],
  rock: [190, 196, 204],
  snow: [246, 249, 252],
  path: '#E8CFA8',
  house: '#F2B3C6',
  shop: '#F6D77A',
  shrine: '#C3B4EC',
  lighthouse: '#FFFFFF',
  tower: '#9FD6EA',
  player: '#367F9A',
})
const MINI = 176
const FULL = 560
// 底图分辨率和小地图看到的范围（米）
const BASE = 512
const MINI_SPAN = 180

function paintBase() {
  const canvas = document.createElement('canvas')
  canvas.width = BASE
  canvas.height = BASE
  const ctx = canvas.getContext('2d')
  const image = ctx.createImageData(BASE, BASE)
  const half = WORLD.HALF
  for (let py = 0; py < BASE; py++)
    for (let px = 0; px < BASE; px++) {
      const x = (px / BASE) * 2 * half - half
      const z = half - (py / BASE) * 2 * half
      const h = heightAt(x, z)
      let c
      if (h < -2) c = MapColor.deep
      else if (h < 0) c = MapColor.shallow
      else if (h > SNOW_LINE && slopeAt(x, z) < 0.8) c = MapColor.snow
      else if (slopeAt(x, z) > 0.9) c = MapColor.rock
      else if (h < SAND_LINE && surfaceNear(x, z).sand > 0.4) c = MapColor.sand
      else {
        const k = Math.min(1, h / 80)
        const base = MapColor.low.map((v, i) => v + (MapColor.high[i] - v) * k)
        c = base.map((v, i) => v + (MapColor.woods[i] - v) * surfaceNear(x, z).woods * 0.7)
      }
      // 简单的山体阴影：西北方向亮，东南暗
      const shade = 1 + Math.max(-0.25, Math.min(0.25, (heightAt(x - 3, z + 3) - h) * -0.04))
      image.data.set([c[0] * shade, c[1] * shade, c[2] * shade, 255], (py * BASE + px) * 4)
    }
  ctx.putImageData(image, 0, 0)
  const toPx = (x, z) => [((x + half) / (2 * half)) * BASE, ((half - z) / (2 * half)) * BASE]
  ctx.strokeStyle = MapColor.path
  ctx.lineWidth = 2
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
    ctx.fillRect(x, y, (h.w / (2 * half)) * BASE, (h.d / (2 * half)) * BASE)
  }
  return canvas
}

// 地图：右下角一块跟着自己走的小地图，点一下或按 M 放大成整座岛；北在上
export default defineComponent({
  name: 'GameMap',
  props: {
    open: { type: Boolean, default: false },
    shrine: { type: Object, required: true },
    // 返回 { x, z, yaw, heading }，每帧读一次
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
      { key: 'tower', color: MapColor.tower },
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
      const me = props.locate()
      if (!ctx || !me) return
      const n = size.value
      const half = WORLD.HALF
      // 小地图以自己为中心只看附近，放大后看整座岛
      const span = props.open ? half : MINI_SPAN / 2
      const cx = props.open ? 0 : me.x
      const cz = props.open ? 0 : me.z
      const toPx = (x, z) => [((x - cx + span) / (2 * span)) * n, ((cz - z + span) / (2 * span)) * n]
      const sx = ((cx - span + half) / (2 * half)) * BASE
      const sy = ((half - cz - span) / (2 * half)) * BASE
      const sw = ((2 * span) / (2 * half)) * BASE
      ctx.fillStyle = 'rgb(150 204 228)'
      ctx.fillRect(0, 0, n, n)
      ctx.drawImage(base, sx, sy, sw, sw, 0, 0, n, n)
      const r = props.open ? 6 : 5
      for (const tower of TOWERS) marker(ctx, ...toPx(tower.x, tower.z), MapColor.tower, r - 1)
      marker(ctx, ...toPx(SHOP.x, SHOP.z), MapColor.shop, r)
      marker(ctx, ...toPx(PLACES.lighthouse.x, PLACES.lighthouse.z), MapColor.lighthouse, r)
      for (const island of SKY_ISLANDS) {
        ctx.beginPath()
        ctx.arc(...toPx(island.x, island.z), (island.r / (2 * span)) * n, 0, Math.PI * 2)
        ctx.setLineDash([4, 4])
        ctx.strokeStyle = MapColor.shrine
        ctx.lineWidth = 2
        ctx.stroke()
        ctx.setLineDash([])
      }
      marker(ctx, ...toPx(props.shrine.x, props.shrine.z), MapColor.shrine, r)
      const [px, py] = toPx(me.x, me.z)
      // 视野扇形和角色箭头；屏幕上 y 向下，所以 z 方向取反
      const a = Math.atan2(-Math.cos(me.heading), Math.sin(me.heading))
      ctx.beginPath()
      ctx.moveTo(px, py)
      ctx.arc(px, py, props.open ? 26 : 22, a - 0.55, a + 0.55)
      ctx.closePath()
      ctx.fillStyle = 'rgb(255 255 255 / 45%)'
      ctx.fill()
      const s = props.open ? 9 : 7
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
