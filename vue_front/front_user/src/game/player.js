// 团子的移动：走、冲刺、跳、攀爬（陡坡和标了能爬的墙）、展开叶子滑翔、游泳，冲刺、攀爬、滑翔和游泳都耗耐力。
// 状态只是普通对象，每帧由世界调用 update；声音和特效通过 events 回调交出去
import { UPDRAFTS, WATER, WORLD, gradientAt, heightAt } from './terrain'
import { BODY } from './collision'
import { DANGO_RADIUS, Pose } from './dango'

export const Mode = Object.freeze({ GROUND: 'ground', AIR: 'air', CLIMB: 'climb', GLIDE: 'glide', SWIM: 'swim' })

export const Stamina = Object.freeze({
  MAX: 100,
  REGEN: 40,
  DELAY: 0.6,
  SPRINT: 16,
  CLIMB: 8,
  CLIMB_IDLE: 1.5,
  CLIMB_JUMP: 16,
  GLIDE: 3,
  SWIM: 1.2,
  SWIM_FAST: 20,
})

const Move = Object.freeze({
  WALK: 6,
  SPRINT: 11,
  JUMP: 9.4,
  GRAVITY: 25,
  AIR_CONTROL: 3,
  CLIMB: 2.8,
  CLIMB_JUMP: 7,
  GLIDE: 9.5,
  GLIDE_FALL: 2.2,
  UPDRAFT: 13,
  SWIM: 3.2,
  SWIM_FAST: 5.8,
  TURN: 12,
  // 比这个陡的地面走不上去，要爬
  WALK_SLOPE: 1.1,
  // 从比这高的地方落地会扬起尘土
  HARD_LANDING: -11,
})

// 浮在水里时身体中心比水面低多少
const FLOAT = 0.62
const SAFE_EVERY = 0.5

function turnToward(from, to, k) {
  return from + Math.atan2(Math.sin(to - from), Math.cos(to - from)) * k
}

export function createPlayer(start, colliders, events) {
  const s = {
    mode: Mode.GROUND,
    x: start.x,
    y: Math.max(heightAt(start.x, start.z), colliders.support(start.x, start.z, start.y + 1)),
    z: start.z,
    vx: 0,
    vy: 0,
    vz: 0,
    yaw: start.yaw,
    stamina: Stamina.MAX,
    max: Stamina.MAX,
    exhausted: false,
    rest: 0,
    surface: null,
    climbBoost: 0,
    airTime: 0,
    safe: { x: start.x, y: start.y, z: start.z },
    safeTimer: 0,
    stepPhase: 0,
    poseSpeed: 0,
  }
  if (heightAt(s.x, s.z) < WATER - 1) s.mode = Mode.SWIM

  function ground(x, z, y) {
    return Math.max(heightAt(x, z), colliders.support(x, z, y))
  }

  function spend(amount) {
    s.stamina = Math.max(0, s.stamina - amount)
    s.rest = Stamina.DELAY
    if (s.stamina === 0 && !s.exhausted) {
      s.exhausted = true
      events.exhausted()
    }
  }
  const canSpend = () => !s.exhausted && s.stamina > 0

  function regen(dt) {
    if (s.rest > 0) s.rest -= dt
    else s.stamina = Math.min(s.max, s.stamina + Stamina.REGEN * dt)
    if (s.exhausted && s.stamina >= s.max) s.exhausted = false
  }

  function enter(mode) {
    if (s.mode === mode) return
    const from = s.mode
    s.mode = mode
    s.airTime = 0
    if (mode === Mode.GLIDE) events.glide(true)
    if (from === Mode.GLIDE) events.glide(false)
    if (mode === Mode.SWIM) events.splash(s.x, s.z)
    if (mode === Mode.CLIMB) events.grab()
  }

  // 开始爬：surface 是 { kind: 'terrain' } 或者某个碰撞体和它朝外的法线
  function grab(surface) {
    if (!canSpend()) return false
    s.surface = surface
    s.vx = s.vz = s.vy = 0
    enter(Mode.CLIMB)
    return true
  }

  // 水平移动一步：先看地形是不是陡得走不上去，再让碰撞体把身体推出来；返回是否开始攀爬
  function travel(dx, dz, dir) {
    let nx = s.x + dx
    let nz = s.z + dz
    const g = gradientAt(nx, nz)
    const rise = heightAt(nx, nz) - s.y
    if (g.slope > Move.WALK_SLOPE && rise > 0.35) {
      const push = dir.x * g.x + dir.z * g.z
      if (push > 0.55 && grab({ kind: 'terrain' })) return true
      // 撞上陡坡：只保留沿着坡走的那部分
      const into = dx * g.x + dz * g.z
      if (into > 0) {
        nx -= g.x * into
        nz -= g.z * into
      }
    }
    const hit = colliders.resolve(nx, nz, s.y, DANGO_RADIUS)
    s.x = hit.x
    s.z = hit.z
    if (hit.wall?.shape.climb && dir.x * -hit.wall.nx + dir.z * -hit.wall.nz > 0.6)
      return grab({ kind: 'shape', shape: hit.wall.shape, nx: hit.wall.nx, nz: hit.wall.nz })
    const r = Math.hypot(s.x, s.z)
    if (r > WORLD.WALK) {
      s.x *= WORLD.WALK / r
      s.z *= WORLD.WALK / r
    }
    return false
  }

  function face(dir, dt, k = 1) {
    if (dir.len > 0.05) s.yaw = turnToward(s.yaw, Math.atan2(dir.x, dir.z), Math.min(1, dt * Move.TURN * k))
  }

  // 落到地面或水面；空中和滑翔共用
  function land(prev) {
    const g = ground(s.x, s.z, s.y)
    if (heightAt(s.x, s.z) < WATER - 1 && s.y <= WATER - FLOAT && g < WATER - FLOAT) {
      s.y = WATER - FLOAT
      s.vy = 0
      enter(Mode.SWIM)
      return
    }
    if (s.y <= g) {
      s.y = g
      if (prev < Move.HARD_LANDING) events.land(s.x, s.y, s.z, prev)
      else events.step(s.x, s.z, true)
      s.vy = 0
      enter(Mode.GROUND)
    }
  }

  function updateGround(dt, input, dir) {
    const sprinting = input.sprint && dir.len > 0.1 && canSpend()
    const speed = sprinting ? Move.SPRINT : Move.WALK
    if (sprinting) spend(Stamina.SPRINT * dt)
    else regen(dt)
    s.vx += (dir.x * speed - s.vx) * Math.min(1, dt * 12)
    s.vz += (dir.z * speed - s.vz) * Math.min(1, dt * 12)
    face(dir, dt)
    if (input.jump) {
      s.vy = Move.JUMP
      enter(Mode.AIR)
      events.jump()
      travel(s.vx * dt, s.vz * dt, dir)
      return
    }
    if (travel(s.vx * dt, s.vz * dt, dir)) return
    const g = ground(s.x, s.z, s.y)
    if (heightAt(s.x, s.z) < WATER - 1 && g < WATER - FLOAT + 0.2) {
      s.y = WATER - FLOAT
      enter(Mode.SWIM)
      return
    }
    if (s.y - g > 0.6) {
      s.vy = 0
      enter(Mode.AIR)
      return
    }
    s.y = g
    // 站在太陡的地上会往下滑
    const slope = gradientAt(s.x, s.z)
    if (slope.slope > Move.WALK_SLOPE * 1.15 && colliders.support(s.x, s.z, s.y) < s.y - 0.2) {
      s.x -= slope.x * dt * 5
      s.z -= slope.z * dt * 5
      s.y = heightAt(s.x, s.z)
    }
    const moving = Math.hypot(s.vx, s.vz)
    s.stepPhase += moving * dt * 0.55
    if (s.stepPhase > 1) {
      s.stepPhase -= 1
      events.step(s.x, s.z, false, sprinting)
    }
    s.poseSpeed = moving
    s.safeTimer += dt
    if (s.safeTimer > SAFE_EVERY && heightAt(s.x, s.z) > WATER + 0.3) {
      s.safeTimer = 0
      s.safe = { x: s.x, y: s.y, z: s.z }
    }
  }

  function updateAir(dt, input, dir) {
    s.airTime += dt
    const prev = s.vy
    s.vy -= Move.GRAVITY * dt
    const keep = Math.max(Math.hypot(s.vx, s.vz), Move.WALK * 0.8)
    s.vx += (dir.x * keep - s.vx) * Math.min(1, dt * Move.AIR_CONTROL) * dir.len
    s.vz += (dir.z * keep - s.vz) * Math.min(1, dt * Move.AIR_CONTROL) * dir.len
    face(dir, dt, 0.5)
    // 跳到最高点附近再按跳跃就展开叶子
    if (input.jump && s.airTime > 0.15 && s.y - ground(s.x, s.z, s.y) > 1.5 && canSpend()) {
      enter(Mode.GLIDE)
      return
    }
    s.y += s.vy * dt
    const ceiling = colliders.ceiling(s.x, s.z, s.y)
    if (s.y + BODY.HEIGHT > ceiling) {
      s.y = ceiling - BODY.HEIGHT
      s.vy = Math.min(0, s.vy)
    }
    if (travel(s.vx * dt, s.vz * dt, dir)) return
    land(prev)
  }

  function updateGlide(dt, input, dir) {
    spend(Stamina.GLIDE * dt)
    // 不按方向键时几乎悬停，方便在上升气流里一直往上升
    const want = dir.len > 0.1 ? dir : { x: Math.sin(s.yaw), z: Math.cos(s.yaw), len: 0.15 }
    s.vx += (want.x * Move.GLIDE * want.len - s.vx) * Math.min(1, dt * 2)
    s.vz += (want.z * Move.GLIDE * want.len - s.vz) * Math.min(1, dt * 2)
    face(want, dt, 0.35)
    let lift = -Move.GLIDE_FALL
    for (const u of UPDRAFTS) {
      const d = Math.hypot(s.x - u.x, s.z - u.z)
      if (d < u.r && s.y < u.top) lift = Move.UPDRAFT
      // 气流边上会被轻轻吸向中间
      if (d < u.r * 1.6 && d > 0.5 && s.y < u.top) {
        s.vx += ((u.x - s.x) / d) * dt * 4
        s.vz += ((u.z - s.z) / d) * dt * 4
      }
    }
    s.vy += (lift - s.vy) * Math.min(1, dt * (lift > 0 ? 2.5 : 4))
    if (input.jump || s.exhausted) {
      enter(Mode.AIR)
      return
    }
    const prev = s.vy
    s.y += s.vy * dt
    if (travel(s.vx * dt, s.vz * dt, dir)) return
    land(prev)
  }

  function updateClimb(dt, input) {
    const surface = s.surface
    const up = input.forward
    const side = input.side
    const moving = Math.abs(up) + Math.abs(side) > 0.1
    spend((moving ? Stamina.CLIMB : Stamina.CLIMB_IDLE) * dt)
    if (s.exhausted) {
      s.vy = 0
      enter(Mode.AIR)
      return
    }
    let boost = 0
    if (input.jump && up >= 0 && s.stamina >= Stamina.CLIMB_JUMP) {
      spend(Stamina.CLIMB_JUMP)
      s.climbBoost = 0.28
      events.jump()
    } else if (input.jump && up < 0) {
      // 往下按着跳：蹬墙离开
      const n = surface.kind === 'terrain' ? gradientAt(s.x, s.z) : { x: -surface.nx, z: -surface.nz }
      s.vx = -n.x * 4
      s.vz = -n.z * 4
      s.vy = 5
      enter(Mode.AIR)
      return
    }
    if (s.climbBoost > 0) {
      s.climbBoost -= dt
      boost = Move.CLIMB_JUMP
    }
    const speed = Move.CLIMB * (up + boost / Move.CLIMB)
    s.poseSpeed = moving || boost ? 1 : 0
    if (surface.kind === 'terrain') {
      const g = gradientAt(s.x, s.z)
      if (g.slope < Move.WALK_SLOPE * 0.85) {
        // 爬到顶了：往前迈一步站上去
        s.x += g.x * 0.5
        s.z += g.z * 0.5
        s.y = heightAt(s.x, s.z)
        enter(Mode.GROUND)
        return
      }
      const k = dt / Math.sqrt(1 + g.slope * g.slope)
      s.x += (g.x * speed + g.z * side * Move.CLIMB) * k
      s.z += (g.z * speed - g.x * side * Move.CLIMB) * k
      s.y = heightAt(s.x, s.z)
      s.yaw = turnToward(s.yaw, Math.atan2(g.x, g.z), Math.min(1, dt * 10))
      if (heightAt(s.x, s.z) < WATER - FLOAT) {
        s.y = WATER - FLOAT
        enter(Mode.SWIM)
        return
      }
      const after = gradientAt(s.x, s.z)
      if (up < 0 && after.slope < Move.WALK_SLOPE * 0.9) enter(Mode.GROUND)
      return
    }
    // 爬墙：沿着法线贴住表面，上下改高度，左右沿着面走
    const shape = surface.shape
    s.y += speed * dt
    const tx = -surface.nz
    const tz = surface.nx
    let x = s.x + tx * side * Move.CLIMB * dt
    let z = s.z + tz * side * Move.CLIMB * dt
    const c = colliders.closest(shape, x, z)
    surface.nx = c.nx
    surface.nz = c.nz
    x = c.px + c.nx * DANGO_RADIUS
    z = c.pz + c.nz * DANGO_RADIUS
    s.x = x
    s.z = z
    s.yaw = turnToward(s.yaw, Math.atan2(-c.nx, -c.nz), Math.min(1, dt * 10))
    if (s.y > shape.top - 0.9 && up > 0) {
      s.x -= c.nx * (DANGO_RADIUS + 0.6)
      s.z -= c.nz * (DANGO_RADIUS + 0.6)
      s.y = shape.top
      enter(Mode.GROUND)
      return
    }
    const g = ground(s.x, s.z, s.y)
    if (s.y <= g) {
      s.y = g
      enter(Mode.GROUND)
    }
  }

  function updateSwim(dt, input, dir) {
    const fast = input.sprint && dir.len > 0.1 && canSpend()
    spend((fast ? Stamina.SWIM_FAST : dir.len > 0.1 ? Stamina.SWIM : 0) * dt)
    if (!fast && dir.len < 0.1) regen(dt * 0.4)
    const speed = fast ? Move.SWIM_FAST : Move.SWIM
    s.vx += (dir.x * speed - s.vx) * Math.min(1, dt * 6)
    s.vz += (dir.z * speed - s.vz) * Math.min(1, dt * 6)
    face(dir, dt, 0.7)
    s.poseSpeed = Math.hypot(s.vx, s.vz) / Move.SWIM
    if (s.stamina <= 0) {
      events.drown()
      respawn()
      return
    }
    if (input.jump) {
      s.vy = 5
      enter(Mode.AIR)
      return
    }
    if (travel(s.vx * dt, s.vz * dt, dir)) return
    const g = ground(s.x, s.z, s.y)
    if (g > WATER - FLOAT - 0.15) {
      s.y = g
      enter(Mode.GROUND)
      return
    }
    s.y = WATER - FLOAT
  }

  function respawn() {
    s.x = s.safe.x
    s.y = s.safe.y
    s.z = s.safe.z
    s.vx = s.vy = s.vz = 0
    s.stamina = s.max
    s.exhausted = false
    enter(Mode.GROUND)
  }

  // input: forward、side 在 -1 到 1，sprint、jump（这一帧刚按下）；heading 是相机看的方向
  function update(dt, input, heading) {
    const fx = Math.sin(heading)
    const fz = Math.cos(heading)
    let x = fx * input.forward + fz * input.side
    let z = fz * input.forward - fx * input.side
    const raw = Math.hypot(x, z)
    const len = Math.min(1, raw)
    if (raw > 0) {
      x /= raw
      z /= raw
    }
    const dir = { x, z, len }
    if (s.mode === Mode.GROUND) updateGround(dt, input, dir)
    else if (s.mode === Mode.AIR) updateAir(dt, input, dir)
    else if (s.mode === Mode.GLIDE) updateGlide(dt, input, dir)
    else if (s.mode === Mode.CLIMB) updateClimb(dt, input)
    else updateSwim(dt, input, dir)
    if (s.y < -30) respawn()
  }

  // 给模型用的动作和渲染位置：爬坡时身体离开坡面一点
  function pose() {
    const moving = s.poseSpeed
    if (s.mode === Mode.GROUND) return { kind: moving > Move.WALK + 1 ? Pose.RUN : moving > 0.3 ? Pose.WALK : Pose.IDLE, k: Math.min(1, moving / Move.WALK) }
    if (s.mode === Mode.AIR) return { kind: Pose.AIR, k: 0 }
    if (s.mode === Mode.GLIDE) return { kind: Pose.GLIDE, k: 1 }
    if (s.mode === Mode.SWIM) return { kind: Pose.SWIM, k: Math.min(1, moving) }
    return { kind: Pose.CLIMB, k: moving }
  }

  function renderOffset() {
    if (s.mode !== Mode.CLIMB || s.surface?.kind !== 'terrain') return { x: 0, z: 0 }
    const g = gradientAt(s.x, s.z)
    return { x: -g.x * DANGO_RADIUS * 0.8, z: -g.z * DANGO_RADIUS * 0.8 }
  }

  return { state: s, update, pose, renderOffset, respawn }
}
