// 碰撞体：竖着的圆柱和正南正北的盒子，各有底和顶的高度。角色能站到顶上，能被侧面挡住，标了 climb 的侧面能爬

const CELL = 16
// 角色身体占的高度范围，脚下这么高以内的台阶直接迈上去
export const BODY = Object.freeze({ HEIGHT: 1.2, STEP: 0.55 })

export function createColliders() {
  const grid = new Map()
  const key = (cx, cz) => cx + ',' + cz

  function insert(shape, minX, maxX, minZ, maxZ) {
    for (let cx = Math.floor(minX / CELL); cx <= Math.floor(maxX / CELL); cx++)
      for (let cz = Math.floor(minZ / CELL); cz <= Math.floor(maxZ / CELL); cz++) {
        const list = grid.get(key(cx, cz)) ?? []
        list.push(shape)
        grid.set(key(cx, cz), list)
      }
    return shape
  }

  // 圆柱：树干、灯塔、大石头、浮空岛
  function addCircle(x, z, r, bottom, top, climb = false) {
    return insert({ kind: 'circle', x, z, r, bottom, top, climb }, x - r, x + r, z - r, z + r)
  }

  // 盒子：房子、瞭望台、摊位
  function addBox(x, z, w, d, bottom, top, climb = false) {
    return insert({ kind: 'box', x, z, hw: w / 2, hd: d / 2, bottom, top, climb }, x - w / 2, x + w / 2, z - d / 2, z + d / 2)
  }

  function near(x, z, reach) {
    const out = new Set()
    for (let cx = Math.floor((x - reach) / CELL); cx <= Math.floor((x + reach) / CELL); cx++)
      for (let cz = Math.floor((z - reach) / CELL); cz <= Math.floor((z + reach) / CELL); cz++)
        for (const s of grid.get(key(cx, cz)) ?? []) out.add(s)
    return out
  }

  // 水平方向上离形状表面最近的点和朝外的法线；inside 表示圆心已经在形状里
  function closest(shape, x, z) {
    if (shape.kind === 'circle') {
      const dx = x - shape.x
      const dz = z - shape.z
      const d = Math.hypot(dx, dz) || 1e-6
      return { px: shape.x + (dx / d) * shape.r, pz: shape.z + (dz / d) * shape.r, nx: dx / d, nz: dz / d, gap: d - shape.r }
    }
    const lx = x - shape.x
    const lz = z - shape.z
    const inside = Math.abs(lx) < shape.hw && Math.abs(lz) < shape.hd
    if (inside) {
      // 从最近的一边出去
      const ex = shape.hw - Math.abs(lx)
      const ez = shape.hd - Math.abs(lz)
      return ex < ez
        ? { px: shape.x + Math.sign(lx || 1) * shape.hw, pz: z, nx: Math.sign(lx || 1), nz: 0, gap: -ex }
        : { px: x, pz: shape.z + Math.sign(lz || 1) * shape.hd, nx: 0, nz: Math.sign(lz || 1), gap: -ez }
    }
    const px = Math.max(shape.x - shape.hw, Math.min(x, shape.x + shape.hw))
    const pz = Math.max(shape.z - shape.hd, Math.min(z, shape.z + shape.hd))
    const d = Math.hypot(x - px, z - pz) || 1e-6
    return { px, pz, nx: (x - px) / d, nz: (z - pz) / d, gap: d }
  }

  // 把身体从侧面挡住它的形状里推出来；返回修正后的位置和贴着的那面墙
  function resolve(x, z, y, radius) {
    let wall = null
    for (const s of near(x, z, radius + 1)) {
      if (s.top <= y + BODY.STEP || s.bottom >= y + BODY.HEIGHT) continue
      const c = closest(s, x, z)
      if (c.gap < radius) {
        x = c.px + c.nx * radius
        z = c.pz + c.nz * radius
        if (!wall || c.gap < wall.gap) wall = { shape: s, nx: c.nx, nz: c.nz, gap: c.gap }
      }
    }
    return { x, z, wall }
  }

  // 脚下能站的最高顶面（不高于 y + 台阶）；没有就返回 -Infinity
  function support(x, z, y, radius = 0.3) {
    let best = -Infinity
    for (const s of near(x, z, radius + 1)) {
      if (s.top > y + BODY.STEP || s.top < best) continue
      const c = closest(s, x, z)
      if (c.gap < radius * 0.5) best = s.top
    }
    return best
  }

  // 某处正上方有没有东西挡着头
  function ceiling(x, z, y) {
    for (const s of near(x, z, 1)) if (s.bottom > y && s.bottom < y + BODY.HEIGHT && closest(s, x, z).gap < 0) return s.bottom
    return Infinity
  }

  return { addCircle, addBox, resolve, support, ceiling, closest, near }
}
