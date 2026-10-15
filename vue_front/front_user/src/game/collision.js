// 简单的水平碰撞：树、灯塔、团子这些是圆柱，房子是正南正北的盒子；角色当成一个圆，被推出去

const CELL = 10

export function createColliders() {
  const grid = new Map()
  const boxes = []
  const key = (cx, cz) => cx + ',' + cz

  function addCircle(x, z, r) {
    const c = { x, z, r }
    const cx = Math.floor(x / CELL)
    const cz = Math.floor(z / CELL)
    const list = grid.get(key(cx, cz)) ?? []
    list.push(c)
    grid.set(key(cx, cz), list)
  }

  function addBox(x, z, w, d) {
    boxes.push({ x, z, hw: w / 2, hd: d / 2 })
  }

  // 把半径为 radius 的圆从所有障碍里推出来，返回修正后的位置
  function resolve(x, z, radius) {
    const cx = Math.floor(x / CELL)
    const cz = Math.floor(z / CELL)
    for (let i = -1; i <= 1; i++)
      for (let j = -1; j <= 1; j++)
        for (const c of grid.get(key(cx + i, cz + j)) ?? []) {
          const dx = x - c.x
          const dz = z - c.z
          const min = c.r + radius
          const d = Math.hypot(dx, dz)
          if (d < min && d > 1e-6) {
            x = c.x + (dx / d) * min
            z = c.z + (dz / d) * min
          }
        }
    for (const b of boxes) {
      const nx = Math.max(b.x - b.hw, Math.min(x, b.x + b.hw))
      const nz = Math.max(b.z - b.hd, Math.min(z, b.z + b.hd))
      const dx = x - nx
      const dz = z - nz
      const d = Math.hypot(dx, dz)
      if (d < radius) {
        if (d > 1e-6) {
          x = nx + (dx / d) * radius
          z = nz + (dz / d) * radius
        } else {
          // 圆心已经进了盒子：从最近的一边推出去
          const push = [
            [b.x - b.hw - radius - x, 0],
            [b.x + b.hw + radius - x, 0],
            [0, b.z - b.hd - radius - z],
            [0, b.z + b.hd + radius - z],
          ].sort((p, q) => Math.abs(p[0] + p[1]) - Math.abs(q[0] + q[1]))[0]
          x += push[0]
          z += push[1]
        }
      }
    }
    return [x, z]
  }

  return { addCircle, addBox, resolve }
}
