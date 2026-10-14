// 三层几何线条：远层大而淡、不描点；中层有轮廓和内套小图形；近层小而实，带顶点和连线。
// 背景固定不随页面滚动，层次靠透明度、大小和描边深浅拉开
const layers = Object.freeze([
  {
    count: 3,
    radius: [110, 170],
    stroke: 0.16,
    fill: 0.035,
    width: 1,
    wobble: 0.03,
    spin: [0.008, 0.025],
    drift: [3, 7],
    nested: 0,
    dots: false,
    links: false,
    tokens: ['--meng-category-anime', '--meng-growth-mint-edge', '--meng-category-game-dot'],
  },
  {
    count: 4,
    radius: [55, 90],
    stroke: 0.24,
    fill: 0.03,
    width: 1.5,
    wobble: 0.05,
    spin: [0.02, 0.06],
    drift: [5, 11],
    nested: 0.6,
    dots: false,
    links: false,
    tokens: [
      '--meng-category-anime',
      '--meng-growth-mint-edge',
      '--meng-category-game-dot',
      '--meng-category-daily',
    ],
  },
  {
    count: 5,
    radius: [22, 40],
    stroke: 0.38,
    fill: 0.05,
    width: 1.5,
    wobble: 0.07,
    spin: [0.05, 0.12],
    drift: [8, 16],
    nested: 0.4,
    dots: true,
    links: true,
    tokens: [
      '--meng-category-creation',
      '--meng-category-daily',
      '--meng-category-game-dot',
      '--meng-growth-mint-edge',
      '--meng-category-anime',
    ],
  },
])
const REFERENCE_AREA = 1920 * 1080
const MAX_SCALE = 1
const LINK_DISTANCE = 160
const MAX_PIXEL_RATIO = 1.5
// 视口顶部留给导航栏：这段高度内完全不画，再用一段渐隐过渡
const CLEAR_HEIGHT = 96
const FADE_HEIGHT = 180
const SIDE_BAND = 0.16
const SIDES = Object.freeze([3, 4, 5, 6, 3, 4, 6, 8])
const LINK_TOKEN = '--meng-primary-light-5'
const DOT_FILL_TOKEN = '--meng-surface'
function random([min, max]) {
  return min + Math.random() * (max - min)
}
function positiveModulo(value, size) {
  return ((value % size) + size) % size
}

export function createGeometryField(canvas) {
  const context = canvas.getContext('2d')
  // 顶点和连线节点都预先分配，逐帧原地更新，不在动画里新建对象
  const nodes = []
  let width = 0,
    height = 0,
    elapsed = 0,
    shapes = [],
    colors = {},
    fade = null
  function readColors() {
    const style = getComputedStyle(document.documentElement)
    const names = new Set([LINK_TOKEN, DOT_FILL_TOKEN, ...layers.flatMap((layer) => layer.tokens)])
    colors = Object.fromEntries(
      [...names].map((name) => [name, style.getPropertyValue(name).trim()]),
    )
  }
  const vertexList = (sides) => Array.from({ length: sides }, () => ({ x: 0, y: 0, owner: 0 }))
  // 每层的图形沿竖向均匀分布，左右两侧交替，避免挤成一团
  function createShape(layer, index, total) {
    const band = width * SIDE_BAND
    const x = index % 2 === 0 ? Math.random() * band : width - Math.random() * band
    const y = ((index + Math.random() * 0.8) / total) * height
    const sides = SIDES[Math.floor(Math.random() * SIDES.length)]
    return {
      layer,
      x,
      y,
      radius: random(layer.radius),
      sides,
      rotation: Math.random() * Math.PI * 2,
      spin: random(layer.spin) * (Math.random() < 0.5 ? -1 : 1),
      // 以竖向漂移为主，图形大多留在两侧的空白处
      vx: random([-3, 3]),
      vy: random(layer.drift) * (Math.random() < 0.5 ? -1 : 1),
      breath: random([0.2, 0.4]),
      phase: Math.random() * Math.PI * 2,
      wobbleSpeed: Array.from({ length: sides }, () => random([0.25, 0.7])),
      wobblePhase: Array.from({ length: sides }, () => Math.random() * Math.PI * 2),
      polygon: vertexList(sides),
      inner: Math.random() < layer.nested ? vertexList(sides) : null,
      onScreen: true,
      color: colors[layer.tokens[index % layer.tokens.length]],
    }
  }
  function seed() {
    const scale = Math.min(MAX_SCALE, Math.max(0.5, (width * height) / REFERENCE_AREA))
    shapes = layers.flatMap((layer) => {
      const total = Math.max(2, Math.round(layer.count * scale))
      return Array.from({ length: total }, (_, i) => createShape(layer, i, total))
    })
    shapes.forEach((shape, id) => shape.polygon.forEach((p) => (p.owner = id)))
  }
  // 顶点按时间起伏，图形会缓慢变形
  function layout(shape) {
    const { layer } = shape
    const margin = shape.radius * 1.4
    const centerY = positiveModulo(shape.y + margin, height + margin * 2) - margin
    // 完全在视口外的图形这一帧不计算也不绘制
    shape.onScreen = centerY + margin > 0 && centerY - margin < height
    if (!shape.onScreen) return
    const breath = 1 + 0.06 * Math.sin(elapsed * shape.breath + shape.phase)
    const turn = shape.rotation + shape.spin * elapsed
    shape.polygon.forEach((p, i) => {
      const angle = turn + (i * Math.PI * 2) / shape.sides
      const wobble =
        1 + layer.wobble * Math.sin(elapsed * shape.wobbleSpeed[i] + shape.wobblePhase[i])
      const reach = shape.radius * breath * wobble
      p.x = shape.x + Math.cos(angle) * reach
      p.y = centerY + Math.sin(angle) * reach
      if (shape.inner) {
        const innerAngle = -0.7 * turn + (i * Math.PI * 2) / shape.sides
        const innerReach = reach * 0.56
        const q = shape.inner[i]
        q.x = shape.x + Math.cos(innerAngle) * innerReach
        q.y = centerY + Math.sin(innerAngle) * innerReach
      }
    })
  }
  function trace(polygon) {
    context.beginPath()
    polygon.forEach((p, i) => (i ? context.lineTo(p.x, p.y) : context.moveTo(p.x, p.y)))
    context.closePath()
  }
  function drawShape(shape) {
    const { layer, color, polygon, inner } = shape
    context.fillStyle = color
    context.strokeStyle = color
    context.lineWidth = layer.width
    trace(polygon)
    context.globalAlpha = layer.fill
    context.fill()
    context.globalAlpha = layer.stroke
    context.stroke()
    if (inner) {
      context.lineWidth = layer.width * 0.8
      trace(inner)
      context.globalAlpha = layer.stroke * 0.65
      context.stroke()
    }
    if (layer.dots) {
      context.globalAlpha = layer.stroke
      context.fillStyle = colors[DOT_FILL_TOKEN]
      context.lineWidth = 1.2
      polygon.forEach((p) => {
        context.beginPath()
        context.arc(p.x, p.y, 2.2, 0, Math.PI * 2)
        context.fill()
        context.stroke()
      })
    }
  }
  function drawLinks() {
    context.strokeStyle = colors[LINK_TOKEN]
    context.lineWidth = 1
    for (let i = 0; i < nodes.length; i++) {
      for (let j = i + 1; j < nodes.length; j++) {
        if (nodes[i].owner === nodes[j].owner) continue
        const distance = Math.hypot(nodes[i].x - nodes[j].x, nodes[i].y - nodes[j].y)
        if (distance > LINK_DISTANCE) continue
        context.globalAlpha = (1 - distance / LINK_DISTANCE) * 0.22
        context.beginPath()
        context.moveTo(nodes[i].x, nodes[i].y)
        context.lineTo(nodes[j].x, nodes[j].y)
        context.stroke()
      }
    }
  }
  // 顶部渐隐直接画进画布，不用 CSS 遮罩（遮罩会让整块画布每帧重新合成）
  function eraseTop() {
    context.globalCompositeOperation = 'destination-out'
    context.globalAlpha = 1
    context.fillStyle = fade
    context.fillRect(0, 0, width, FADE_HEIGHT)
    context.globalCompositeOperation = 'source-over'
  }
  function draw() {
    if (!width || !height) return
    context.clearRect(0, 0, width, height)
    context.lineJoin = 'round'
    shapes.forEach(layout)
    // 先画远层再画近层，近层的连线夹在中间
    layers.forEach((layer) => {
      if (layer.links) {
        nodes.length = 0
        shapes.forEach((shape) => {
          if (shape.layer === layer && shape.onScreen) nodes.push(...shape.polygon)
        })
        drawLinks()
      }
      shapes.forEach((shape) => {
        if (shape.layer === layer && shape.onScreen) drawShape(shape)
      })
    })
    eraseTop()
    context.globalAlpha = 1
  }
  function step(delta) {
    elapsed += delta
    shapes.forEach((shape) => {
      shape.x += shape.vx * delta
      shape.y += shape.vy * delta
      const margin = shape.radius * 1.4
      if (shape.x < -margin) shape.x = width + margin
      else if (shape.x > width + margin) shape.x = -margin
    })
  }
  function resize() {
    const rect = canvas.getBoundingClientRect()
    const previousWidth = width,
      previousHeight = height
    width = rect.width
    height = rect.height
    const ratio = Math.min(devicePixelRatio || 1, MAX_PIXEL_RATIO)
    canvas.width = Math.round(width * ratio)
    canvas.height = Math.round(height * ratio)
    context.setTransform(ratio, 0, 0, ratio, 0, 0)
    fade = context.createLinearGradient(0, 0, 0, FADE_HEIGHT)
    fade.addColorStop(0, '#000')
    fade.addColorStop(CLEAR_HEIGHT / FADE_HEIGHT, '#000')
    fade.addColorStop(1, 'transparent')
    if (!shapes.length) {
      readColors()
      seed()
    } else if (previousWidth && previousHeight) {
      shapes.forEach((shape) => {
        shape.x *= width / previousWidth
        shape.y *= height / previousHeight
      })
    }
    draw()
  }
  return { resize, step, draw }
}
