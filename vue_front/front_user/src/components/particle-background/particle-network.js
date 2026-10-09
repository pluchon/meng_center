import { ParticleMotion } from '@/constants/visual'

export function advanceReconnection(point, points, delta, isBlocked) {
  if (!delta || point.reconnectTime <= 0 || point.disperseTime > 0) return
  point.reconnectTime = Math.max(0, point.reconnectTime - delta)
  if (!point.reconnectTime) {
    point.excluded.clear()
    return
  }
  let candidate,
    nearest = ParticleMotion.RECONNECT_RADIUS
  points.forEach((other, index) => {
    if (
      other === point ||
      other.disperseTime > 0 ||
      ((point.componentId !== other.componentId || point.componentId === undefined) &&
        (point.componentSize ?? 1) + (other.componentSize ?? 1) >
          ParticleMotion.MAX_COMPONENT_POINTS) ||
      (other.neighbors?.size ?? 0) >= ParticleMotion.MAX_NEIGHBORS ||
      point.neighbors.has(index) ||
      point.excluded.has(index) ||
      isBlocked(point, other)
    )
      return
    const distance = Math.hypot(other.x - point.x, other.y - point.y)
    if (distance < nearest) {
      nearest = distance
      candidate = other
    }
  })
  if (!candidate || nearest <= ParticleMotion.RECONNECT_SPACING) return
  const step = Math.min(
    ParticleMotion.RECONNECT_SPEED * delta,
    nearest - ParticleMotion.RECONNECT_SPACING,
  )
  const ease = 1 - Math.exp(-ParticleMotion.STEERING_EASE * delta)
  point.pullX = (point.pullX ?? 0) + ((candidate.x - point.x) / nearest - (point.pullX ?? 0)) * ease
  point.pullY = (point.pullY ?? 0) + ((candidate.y - point.y) / nearest - (point.pullY ?? 0)) * ease
  point.x += point.pullX * step
  point.y += point.pullY * step
}

// Bound both graph size and canvas strokes, even when every particle overlaps.
export function buildConnections(points, isBlocked) {
  const parent = points.map((_, index) => index),
    sizes = points.map(() => 1)
  const neighbors = points.map(() => new Set()),
    candidates = [],
    links = []
  function root(index) {
    while (parent[index] !== index) {
      parent[index] = parent[parent[index]]
      index = parent[index]
    }
    return index
  }
  for (let i = 0; i < points.length; i++) {
    for (let j = i + 1; j < points.length; j++) {
      const distance = Math.hypot(points[i].x - points[j].x, points[i].y - points[j].y)
      const existing = points[i].neighbors.has(j)
      const limit = existing ? ParticleMotion.LINK_DISTANCE : ParticleMotion.LINK_START_DISTANCE
      if (distance >= limit || isBlocked(points[i], points[j])) continue
      if ((points[i].disperseTime > 0 || points[j].disperseTime > 0) && !points[i].neighbors.has(j))
        continue
      candidates.push({ i, j, distance, existing })
    }
  }
  candidates.sort((a, b) => Number(b.existing) - Number(a.existing) || a.distance - b.distance)
  for (const link of candidates) {
    const { i, j } = link,
      a = root(i),
      b = root(j)
    if (
      neighbors[i].size >= ParticleMotion.MAX_NEIGHBORS ||
      neighbors[j].size >= ParticleMotion.MAX_NEIGHBORS
    )
      continue
    if (a !== b && sizes[a] + sizes[b] > ParticleMotion.MAX_COMPONENT_POINTS) continue
    if (a !== b) {
      parent[b] = a
      sizes[a] += sizes[b]
    }
    neighbors[i].add(j)
    neighbors[j].add(i)
    links.push(link)
  }
  const components = new Map()
  points.forEach((_, index) => {
    const key = root(index)
    if (!components.has(key)) components.set(key, [])
    components.get(key).push(index)
  })
  points.forEach((point, index) => {
    point.componentId = root(index)
    point.componentSize = sizes[root(index)]
  })
  return { neighbors, links, components: [...components.values()] }
}

export function disperseCrowds(points, components, delta) {
  if (!delta) return
  for (const indices of components) {
    if (indices.length < ParticleMotion.MAX_COMPONENT_POINTS) continue
    const center = indices.reduce(
      (sum, i) => ({
        x: sum.x + points[i].x / indices.length,
        y: sum.y + points[i].y / indices.length,
      }),
      { x: 0, y: 0 },
    )
    indices.forEach((index, order) => {
      const point = points[index]
      if (point.disperseTime > 0) return
      point.disperseTime = ParticleMotion.DISPERSE_SECONDS
      point.reconnectTime = 0
      point.excluded.clear()
      const angle = Math.atan2(point.y - center.y, point.x - center.x)
      // Coincident particles need distinct directions to escape, rather than divide by zero.
      const direction =
        Math.hypot(point.x - center.x, point.y - center.y) < ParticleMotion.RADIUS
          ? (order * Math.PI * 2) / indices.length
          : angle
      point.spreadX = Math.cos(direction)
      point.spreadY = Math.sin(direction)
    })
  }
}

export function advanceDispersion(point, delta) {
  if (!delta || !point.disperseTime) return
  point.disperseTime = Math.max(0, point.disperseTime - delta)
  const ease = 1 - Math.exp(-ParticleMotion.STEERING_EASE * delta)
  point.spreadVelocityX =
    (point.spreadVelocityX ?? 0) + (point.spreadX - (point.spreadVelocityX ?? 0)) * ease
  point.spreadVelocityY =
    (point.spreadVelocityY ?? 0) + (point.spreadY - (point.spreadVelocityY ?? 0)) * ease
  const remaining = Math.min(1, point.disperseTime / ParticleMotion.DISPERSE_SECONDS)
  point.x += point.spreadVelocityX * ParticleMotion.DISPERSE_SPEED * delta * remaining
  point.y += point.spreadVelocityY * ParticleMotion.DISPERSE_SPEED * delta * remaining
}
