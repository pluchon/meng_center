// 萌岛的地形：海岸、草原、森林、湖、巨树高原、雪山和东边的海崖，由固定种子的噪声加几处手工地形拼成。
// 高度先按 2 米一格算好存起来，之后渲染、落脚、攀爬、摆放和地图都查这张表，角色不会陷地也不会浮空

const SEED = 20261015

// 水面高度：海和湖是同一个水面
export const WATER = 0
// 低于这个高度的海边显示沙滩；高于 SNOW_LINE 的缓坡积雪
export const SAND_LINE = 2.6
export const SNOW_LINE = 100

// 世界范围：x、z 都在 ±HALF 米内；GRID 是高度表的格距
export const WORLD = Object.freeze({ HALF: 448, GRID: 2, COAST: 350, WALK: 420 })
const N = WORLD.HALF / WORLD.GRID * 2 + 1

// 地标位置；后端的点位清单也是按这些位置摆的
export const PLACES = Object.freeze({
  spawn: { x: 0, z: -318 },
  village: { x: -30, z: -215, r: 44, h: 9 },
  meadow: { x: 110, z: -210, r: 90 },
  forest: { x: -250, z: -20, r: 120 },
  lake: { x: -60, z: 60, r: 85 },
  islet: { x: -60, z: 60 },
  plateau: { x: -180, z: 240, r: 70, h: 64 },
  mountain: { x: 160, z: 190, r: 170, h: 165 },
  cliffs: { x: 290, z: -40 },
  lighthouse: { x: 300, z: -30 },
})

// 天上的浮空岛：中心、顶面高度和半径；上升气流从地面把人托上去
export const SKY_ISLANDS = Object.freeze([{ x: 20, z: 130, top: 150, r: 26 }])
export const UPDRAFTS = Object.freeze([
  { x: 44, z: 54, r: 9, top: 172 },
  { x: -140, z: 222, r: 8, top: 125 },
])

// 小村的房子：中心、宽（x 方向）、深（z 方向）、墙高
export const HOUSES = Object.freeze([
  { x: -58, z: -196, w: 9, d: 8, h: 4.2 },
  { x: -2, z: -196, w: 8, d: 8, h: 4 },
  { x: -60, z: -236, w: 8, d: 9, h: 4.4 },
  { x: 0, z: -236, w: 9, d: 8, h: 4 },
  { x: -30, z: -252, w: 11, d: 8, h: 5 },
])

// 商店团子站的位置，身后是摊位
export const SHOP = Object.freeze({ x: -30, z: -206 })

// 瞭望台：四方的高塔，能爬上去
export const TOWERS = Object.freeze([
  { x: 96, z: -250, h: 22 },
  { x: -226, z: 52, h: 24 },
  { x: 28, z: 18, h: 20 },
  { x: 242, z: -130, h: 18 },
  { x: 70, z: 118, h: 22 },
])

// 小路：一串折线，贴图换成小路，两侧不长树
export const PATHS = Object.freeze([
  [
    [0, -332],
    [-8, -290],
    [-26, -246],
    [-30, -215],
  ],
  [
    [-30, -215],
    [-38, -150],
    [-46, -80],
    [-30, -24],
    [10, 20],
    [60, 80],
    [92, 130],
  ],
  [
    [-30, -215],
    [50, -200],
    [140, -150],
    [210, -100],
    [262, -56],
    [298, -34],
  ],
  [
    [-30, -215],
    [-110, -195],
    [-190, -120],
    [-236, -40],
    [-230, 40],
  ],
  [
    [-150, 40],
    [-190, 110],
    [-200, 160],
  ],
])

function hash(ix, iz) {
  let h = Math.imul(ix, 374761393) ^ Math.imul(iz, 668265263) ^ SEED
  h = Math.imul(h ^ (h >>> 13), 1274126177)
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296
}

function fade(t) {
  return t * t * (3 - 2 * t)
}

function noise(x, z) {
  const ix = Math.floor(x)
  const iz = Math.floor(z)
  const fx = fade(x - ix)
  const fz = fade(z - iz)
  const a = hash(ix, iz)
  const b = hash(ix + 1, iz)
  const c = hash(ix, iz + 1)
  const d = hash(ix + 1, iz + 1)
  return (a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz) * 2 - 1
}

export function fbm(x, z, octaves = 4) {
  let sum = 0
  let amp = 0.55
  let f = 1
  for (let i = 0; i < octaves; i++) {
    sum += noise(x * f + i * 17.3, z * f - i * 9.1) * amp
    f *= 2.03
    amp *= 0.48
  }
  return sum
}

// 山脊噪声：尖的地方是山脊
function ridged(x, z) {
  let sum = 0
  let amp = 0.6
  let f = 1
  for (let i = 0; i < 4; i++) {
    const n = 1 - Math.abs(noise(x * f + 31 * i, z * f - 7 * i))
    sum += n * n * amp
    f *= 2.1
    amp *= 0.5
  }
  return sum
}

export function smoothstep(from, to, value) {
  const t = Math.min(1, Math.max(0, (value - from) / (to - from)))
  return t * t * (3 - 2 * t)
}

const mix = (a, b, t) => a + (b - a) * t

// 海岸线在某个方向上离岛中心多远
export function coastAt(angle) {
  return WORLD.COAST + 22 * Math.sin(3 * angle + 1) + 14 * Math.sin(7 * angle - 2) + 8 * Math.sin(13 * angle + 0.5)
}

// 东边海崖的程度：0 是普通海岸，1 是陡崖
function cliffSide(x, z) {
  return smoothstep(150, 250, x) * smoothstep(230, 130, Math.abs(z + 30))
}

// 不查表的原始高度，只在建表时用
function rawHeight(x, z) {
  const r = Math.hypot(x, z)
  const cliff = cliffSide(x, z)
  const coast = coastAt(Math.atan2(z, x))
  // 0 在海岸线上，1 是内陆；普通海岸留出几十米宽的缓坡沙滩，海崖那边几米内就陡下去
  const land = smoothstep(coast, coast - mix(90, 8, cliff), r)
  let h = 7 + fbm(x * 0.008, z * 0.008) * 10 + fbm(x * 0.035 + 5, z * 0.035) * 1.8
  h += cliff * 34
  const { mountain, plateau, lake, islet, village } = PLACES
  const dm = Math.hypot(x - mountain.x, z - mountain.z)
  const m = Math.max(0, 1 - dm / mountain.r)
  h += mountain.h * Math.pow(m, 1.7) * (0.78 + 0.32 * ridged(x * 0.011, z * 0.011))
  const dp = Math.hypot(x - plateau.x, z - plateau.z) + fbm(x * 0.05, z * 0.05) * 7
  h = mix(h, plateau.h + fbm(x * 0.02, z * 0.02) * 2, smoothstep(plateau.r + 9, plateau.r - 2, dp))
  const dl = Math.hypot(x - lake.x, z - lake.z) + fbm(x * 0.03, z * 0.03) * 8
  h = mix(h, -8, smoothstep(lake.r, lake.r - 40, dl))
  h = mix(h, 3.2, smoothstep(13, 7, Math.hypot(x - islet.x, z - islet.z)))
  const dv = Math.hypot(x - village.x, z - village.z)
  h = mix(h, village.h, smoothstep(village.r, village.r - 14, dv))
  const beach = -5 + 7.5 * smoothstep(0, 0.35, land)
  const shore = r > coast ? Math.max(-24, -5 - (r - coast) * 0.2) : mix(beach, h, smoothstep(0.3, 0.75, land))
  return mix(shore, h, cliff * smoothstep(0, 0.4, land))
}

let grid = null

// 第一次用到时建高度表，大约 0.2 秒
export function buildTerrain() {
  if (grid) return grid
  grid = new Float32Array(N * N)
  for (let j = 0; j < N; j++)
    for (let i = 0; i < N; i++) grid[j * N + i] = rawHeight(-WORLD.HALF + i * WORLD.GRID, -WORLD.HALF + j * WORLD.GRID)
  return grid
}

// 任意位置的地面高度，表外是海底
export function heightAt(x, z) {
  const g = grid ?? buildTerrain()
  const fx = (x + WORLD.HALF) / WORLD.GRID
  const fz = (z + WORLD.HALF) / WORLD.GRID
  if (fx < 0 || fz < 0 || fx >= N - 1 || fz >= N - 1) return -24
  const i = Math.floor(fx)
  const j = Math.floor(fz)
  const tx = fx - i
  const tz = fz - j
  const a = g[j * N + i]
  const b = g[j * N + i + 1]
  const c = g[(j + 1) * N + i]
  const d = g[(j + 1) * N + i + 1]
  return a + (b - a) * tx + (c - a) * tz + (a - b - c + d) * tx * tz
}

// 坡度：高度对水平距离的变化率，0 是平地，1 是 45 度
export function slopeAt(x, z) {
  const e = 1
  const dx = heightAt(x + e, z) - heightAt(x - e, z)
  const dz = heightAt(x, z + e) - heightAt(x, z - e)
  return Math.hypot(dx, dz) / (2 * e)
}

// 往上坡的方向（单位向量）和坡度
export function gradientAt(x, z) {
  const e = 0.75
  const dx = (heightAt(x + e, z) - heightAt(x - e, z)) / (2 * e)
  const dz = (heightAt(x, z + e) - heightAt(x, z - e)) / (2 * e)
  const g = Math.hypot(dx, dz)
  return { x: g > 1e-6 ? dx / g : 0, z: g > 1e-6 ? dz / g : 0, slope: g }
}

function segmentDistance(x, z, [ax, az], [bx, bz]) {
  const vx = bx - ax
  const vz = bz - az
  const t = Math.min(1, Math.max(0, ((x - ax) * vx + (z - az) * vz) / (vx * vx + vz * vz)))
  return Math.hypot(x - ax - vx * t, z - az - vz * t)
}

// 到最近一条小路的距离
export function pathDistance(x, z) {
  let best = Infinity
  for (const path of PATHS)
    for (let i = 1; i < path.length; i++) best = Math.min(best, segmentDistance(x, z, path[i - 1], path[i]))
  return best
}

// 地表的几种贴图权重：沙滩、小路、森林地面、花海；草地打底，岩石和雪按坡度和高度在着色器里算
export function surfaceAt(x, z) {
  const h = heightAt(x, z)
  const coast = Math.hypot(x, z) > coastAt(Math.atan2(z, x)) - 70 || Math.hypot(x - PLACES.lake.x, z - PLACES.lake.z) < PLACES.lake.r + 6
  const sand = coast ? smoothstep(SAND_LINE + 0.6, SAND_LINE - 0.6, h) : 0
  const path = h > 0.5 ? smoothstep(3.2, 1.6, pathDistance(x, z)) : 0
  const { forest, meadow } = PLACES
  const woods = smoothstep(forest.r, forest.r * 0.6, Math.hypot(x - forest.x, z - forest.z) + fbm(x * 0.02, z * 0.02) * 20)
  const flowers = smoothstep(meadow.r, meadow.r * 0.5, Math.hypot(x - meadow.x, z - meadow.z) + fbm(x * 0.03, z * 0.03) * 25)
  return { sand, path, woods, flowers }
}

// 地表权重表：512 见方，每格存沙滩、小路、森林、花海四个 0 到 255 的权重；地面贴图和花草都查它
export const SURFACE_SIZE = 512
let surface = null

export function buildSurface() {
  if (surface) return surface
  surface = new Uint8Array(SURFACE_SIZE * SURFACE_SIZE * 4)
  for (let py = 0; py < SURFACE_SIZE; py++)
    for (let px = 0; px < SURFACE_SIZE; px++) {
      const x = ((px + 0.5) / SURFACE_SIZE) * 2 * WORLD.HALF - WORLD.HALF
      const z = ((py + 0.5) / SURFACE_SIZE) * 2 * WORLD.HALF - WORLD.HALF
      const s = surfaceAt(x, z)
      surface.set([s.sand * 255, s.path * 255, s.woods * 255, s.flowers * 255], (py * SURFACE_SIZE + px) * 4)
    }
  return surface
}

// 查表版的地表权重，取最近的一格
export function surfaceNear(x, z) {
  const table = surface ?? buildSurface()
  const px = Math.min(SURFACE_SIZE - 1, Math.max(0, Math.floor(((x + WORLD.HALF) / (2 * WORLD.HALF)) * SURFACE_SIZE)))
  const py = Math.min(SURFACE_SIZE - 1, Math.max(0, Math.floor(((z + WORLD.HALF) / (2 * WORLD.HALF)) * SURFACE_SIZE)))
  const i = (py * SURFACE_SIZE + px) * 4
  return { sand: table[i] / 255, path: table[i + 1] / 255, woods: table[i + 2] / 255, flowers: table[i + 3] / 255 }
}

// 某处属于哪个区域，背景音乐和地图用
export function regionAt(x, z) {
  const near = (p, extra = 0) => Math.hypot(x - p.x, z - p.z) < (p.r ?? 40) + extra
  if (heightAt(x, z) > 60 && near(PLACES.mountain)) return 'mountain'
  if (near(PLACES.plateau, 10)) return 'plateau'
  if (near(PLACES.lake, 20)) return 'lake'
  if (near(PLACES.village, 10)) return 'village'
  if (near(PLACES.forest)) return 'forest'
  if (cliffSide(x, z) > 0.5) return 'cliffs'
  if (near(PLACES.meadow, 30)) return 'meadow'
  return Math.hypot(x, z) > 290 ? 'coast' : 'meadow'
}

// 固定种子的伪随机数，树和花每次生成在同样的位置
export function random(seed) {
  let s = seed >>> 0
  return () => {
    s = (s + 0x6d2b79f5) >>> 0
    let t = s
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}
