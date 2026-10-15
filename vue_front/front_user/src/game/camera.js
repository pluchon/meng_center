// 第三人称相机：点一下画面锁定鼠标，之后移动鼠标就转视角（没锁上时按住拖动也行），滚轮拉远拉近；
// 相机和角色之间被地面、树冠或房子挡住时自动拉近，挡住的东西让开后再慢慢退回去
import { ArcRotateCamera } from '@babylonjs/core/Cameras/arcRotateCamera'
import { Vector3 } from '@babylonjs/core/Maths/math.vector'
import { HOUSES, heightAt } from './terrain'

const Look = Object.freeze({ RADIUS: 9, MIN: 4, MAX: 20, CLOSEST: 1.6, HEIGHT: 1.1, SENSITIVITY: 0.0024 })
const CELL = 12

// 树冠按球算：圆树低胖，松树高瘦
function crownOf(tree) {
  const ground = heightAt(tree.x, tree.z)
  return tree.kind === 'pine'
    ? { x: tree.x, y: ground + 3.6 * tree.scale, z: tree.z, r: 1.5 * tree.scale }
    : { x: tree.x, y: ground + 3.7 * tree.scale, z: tree.z, r: 2.0 * tree.scale }
}

export function createFollowCamera(scene, canvas, start, trees, hooks) {
  const camera = new ArcRotateCamera(
    'camera',
    Math.atan2(-Math.cos(start.yaw), -Math.sin(start.yaw)),
    1.2,
    Look.RADIUS,
    new Vector3(start.x, start.y + Look.HEIGHT, start.z),
    scene,
  )
  camera.lowerRadiusLimit = Look.CLOSEST
  camera.upperRadiusLimit = Look.MAX
  camera.lowerBetaLimit = 0.35
  camera.upperBetaLimit = 1.62
  camera.wheelDeltaPercentage = 0.01
  camera.minZ = 0.2
  camera.maxZ = 1000
  // 转视角自己处理，相机自带的只留滚轮
  camera.inputs.removeByType('ArcRotateCameraKeyboardMoveInput')
  camera.inputs.removeByType('ArcRotateCameraPointersInput')
  camera.attachControl(canvas, true)

  const grid = new Map()
  for (const tree of trees) {
    const crown = crownOf(tree)
    const key = Math.floor(crown.x / CELL) + ',' + Math.floor(crown.z / CELL)
    grid.set(key, [...(grid.get(key) ?? []), crown])
  }
  function blocked(x, y, z) {
    if (y < heightAt(x, z) + 0.45) return true
    const cx = Math.floor(x / CELL)
    const cz = Math.floor(z / CELL)
    for (let i = -1; i <= 1; i++)
      for (let j = -1; j <= 1; j++)
        for (const c of grid.get(cx + i + ',' + (cz + j)) ?? [])
          if ((x - c.x) ** 2 + ((y - c.y) * 1.2) ** 2 + (z - c.z) ** 2 < c.r * c.r) return true
    for (const h of HOUSES)
      if (Math.abs(x - h.x) < h.w / 2 + 0.6 && Math.abs(z - h.z) < h.d / 2 + 0.6 && y < heightAt(h.x, h.z) + 6.2) return true
    return false
  }

  let wanted = Look.RADIUS
  let applied = camera.radius
  let locked = false
  let dragging = false
  let releasing = false
  let enabled = true

  function turn(dx, dy) {
    camera.alpha -= dx * Look.SENSITIVITY
    camera.beta = Math.min(camera.upperBetaLimit, Math.max(camera.lowerBetaLimit, camera.beta - dy * Look.SENSITIVITY))
  }
  function lock() {
    if (!enabled || locked || !canvas.requestPointerLock) return
    // 刚按 Esc 退出后马上再锁，浏览器会拒绝，这时退回按住拖动
    Promise.resolve(canvas.requestPointerLock()).catch(() => {})
  }
  function pointerdown(event) {
    if (!enabled || event.button !== 0) return
    dragging = true
    lock()
  }
  function pointermove(event) {
    if (!enabled) return
    if (locked || (dragging && event.buttons & 1)) turn(event.movementX, event.movementY)
  }
  function pointerup() {
    dragging = false
  }
  function lockchange() {
    const now = document.pointerLockElement === canvas
    if (now === locked) return
    locked = now
    hooks.look(locked)
    // 玩着的时候被解锁（按了 Esc 或切走了窗口）就暂停；自己打开面板时的解锁不算
    if (!locked && !releasing) hooks.unlocked()
    releasing = false
  }
  canvas.addEventListener('pointerdown', pointerdown)
  document.addEventListener('pointermove', pointermove)
  document.addEventListener('pointerup', pointerup)
  document.addEventListener('pointerlockchange', lockchange)

  // 每帧跟上角色并避开遮挡
  function update(dt, x, y, z) {
    const follow = new Vector3(x, y + Look.HEIGHT, z)
    // 原地移动目标点：给 camera.target 赋值会按旧位置重算角度，把这一帧鼠标转的视角抵消掉
    camera.target.copyFrom(Vector3.Lerp(camera.target, follow, Math.min(1, dt * 8)))
    if (Math.abs(camera.radius - applied) > 1e-3) wanted = Math.min(Look.MAX, Math.max(Look.MIN, camera.radius))
    const sb = Math.sin(camera.beta)
    const dx = Math.cos(camera.alpha) * sb
    const dy = Math.cos(camera.beta)
    const dz = Math.sin(camera.alpha) * sb
    const t = camera.target
    let room = wanted
    for (let d = 0.8; d <= wanted; d += 0.4)
      if (blocked(t.x + dx * d, t.y + dy * d, t.z + dz * d)) {
        room = Math.max(Look.CLOSEST, d - 0.5)
        break
      }
    camera.radius = room < camera.radius ? room : camera.radius + (room - camera.radius) * Math.min(1, dt * 3)
    applied = camera.radius
  }

  return {
    camera,
    update,
    // 打开面板或暂停时放开鼠标，不当成玩家主动暂停
    release() {
      enabled = false
      dragging = false
      if (locked) {
        releasing = true
        document.exitPointerLock()
      }
    },
    // 继续游戏：要在点击事件里调用，浏览器才允许重新锁定鼠标
    resume() {
      enabled = true
      lock()
    },
    // 角色前方的方向，地图上画视野用
    heading() {
      return Math.atan2(-Math.cos(camera.alpha), -Math.sin(camera.alpha))
    },
    dispose() {
      canvas.removeEventListener('pointerdown', pointerdown)
      document.removeEventListener('pointermove', pointermove)
      document.removeEventListener('pointerup', pointerup)
      document.removeEventListener('pointerlockchange', lockchange)
      if (locked) document.exitPointerLock()
    },
  }
}
