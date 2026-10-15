// 萌岛的地形：固定种子的值噪声加岛形遮罩。渲染、落脚和摆放都用这一个函数，角色不会陷地也不会浮空

const SEED = 20261015

// 岛的尺寸：radius 以外是海底，walk 是能走到的最远处（刚没过脚面）
export const ISLAND = { radius: 118, walk: 104, seaFloor: -4, size: 280 }

// 低于这个高度显示沙滩
export const SAND_LINE = 1.6

// 地标位置；后端的点位清单也是按这些位置摆的
export const PLACES = {
  spawn: { x: 0, z: -100 },
  village: { x: -46, z: 8, r: 20, h: 3.2 },
  hill: { x: 44, z: 42, r: 24, h: 26, top: 6 },
  lighthouse: { x: 88, z: -30 },
  forest: { x: -50, z: 58, r: 32 },
}

// 小村的房子：中心、宽（x 方向）、深（z 方向），都是正南正北摆的
export const HOUSES = [
  { x: -58, z: 20, w: 7, d: 6 },
  { x: -33, z: 20, w: 6, d: 6 },
  { x: -58, z: -6, w: 6, d: 7 },
  { x: -33, z: -6, w: 7, d: 6 },
]

// 商店团子站的位置，身后是摊位
export const SHOP = { x: -46, z: 12 }

// 小路：一串折线，贴图换成小路，两侧不长树
export const PATHS = [
  [
    [0, -98],
    [0, -60],
    [-20, -26],
    [-46, 8],
  ],
  [
    [-46, 8],
    [-12, 20],
    [20, 24],
    [32, 32],
    [44, 42],
  ],
  [
    [0, -60],
    [40, -50],
    [84, -30],
  ],
  [
    [-46, 8],
    [-50, 36],
  ],
]

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

function fbm(x, z) {
  return noise(x, z) * 0.6 + noise(x * 2.1 + 17, z * 2.1 - 9) * 0.28 + noise(x * 4.3 - 31, z * 4.3 + 5) * 0.12
}

export function smoothstep(from, to, value) {
  const t = Math.min(1, Math.max(0, (value - from) / (to - from)))
  return t * t * (3 - 2 * t)
}

function rolling(x, z) {
  return 2.6 + fbm(x * 0.035, z * 0.035) * 2.2
}

const { hill, village } = PLACES
const HILL_TOP = rolling(hill.x, hill.z) + hill.h

// 任意位置的地面高度
export function heightAt(x, z) {
  const land = smoothstep(ISLAND.radius, ISLAND.radius - 26, Math.hypot(x, z))
  let h = rolling(x, z)
  const dh = Math.hypot(x - hill.x, z - hill.z)
  h += hill.h * Math.exp(-(dh * dh) / (2 * hill.r * hill.r))
  h += (HILL_TOP - h) * smoothstep(hill.top + 4, hill.top, dh)
  const dv = Math.hypot(x - village.x, z - village.z)
  h += (village.h - h) * smoothstep(village.r, village.r - 8, dv)
  return h * land + ISLAND.seaFloor * (1 - land)
}

// 用差分估计坡度，0 是平地，越大越陡
export function slopeAt(x, z) {
  const e = 0.6
  const dx = heightAt(x + e, z) - heightAt(x - e, z)
  const dz = heightAt(x, z + e) - heightAt(x, z - e)
  return Math.hypot(dx, dz) / (2 * e)
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
