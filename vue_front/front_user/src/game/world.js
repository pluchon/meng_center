// 萌岛的 3D 世界：引擎、光照、键盘、角色移动和每帧更新。只认普通对象，不进 Vue 的响应式
import { Engine } from '@babylonjs/core/Engines/engine'
import { Scene } from '@babylonjs/core/scene'
import { HemisphericLight } from '@babylonjs/core/Lights/hemisphericLight'
import { DirectionalLight } from '@babylonjs/core/Lights/directionalLight'
import { ShadowGenerator } from '@babylonjs/core/Lights/Shadows/shadowGenerator'
import '@babylonjs/core/Lights/Shadows/shadowGeneratorSceneComponent'
import { GlowLayer } from '@babylonjs/core/Layers/glowLayer'
import '@babylonjs/core/Layers/effectLayerSceneComponent'
import { MeshBuilder } from '@babylonjs/core/Meshes/meshBuilder'
import { StandardMaterial } from '@babylonjs/core/Materials/standardMaterial'
import { Color3, Color4 } from '@babylonjs/core/Maths/math.color'
import { Vector3 } from '@babylonjs/core/Maths/math.vector'
import { Prompt, SpotKind } from '@/constants/game'
import { ISLAND, SHOP, heightAt } from './terrain'
import { createColliders } from './collision'
import { createScenery } from './scenery'
import { createPickups } from './pickups'
import { createEffects } from './effects'
import { createFollowCamera } from './camera'
import { windClock } from './wind'
import { DANGO_RADIUS, Face, createDango } from './dango'

const Move = Object.freeze({ WALK: 6.5, RUN: 11, JUMP: 9.5, GRAVITY: 26, TURN: 12, WADE: 0.55 })
// 天色一个来回的秒数，白天和傍晚之间慢慢变
const DAY_SECONDS = 480
const SHADOW_SIZE = 1024
const SHOP_REACH = 3.2
const SHRINE_REACH = 6
// 团子老板在这个距离内会转过来看着你
const NPC_WATCH = 9

const KEYS = Object.freeze({
  forward: ['KeyW', 'ArrowUp'],
  back: ['KeyS', 'ArrowDown'],
  left: ['KeyA', 'ArrowLeft'],
  right: ['KeyD', 'ArrowRight'],
})

function lerpColor(a, b, t) {
  return new Color3(a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t, a.b + (b.b - a.b) * t)
}

// 角度插值走最短的那一边
function turnToward(from, to, k) {
  const diff = Math.atan2(Math.sin(to - from), Math.cos(to - from))
  return from + diff * k
}

// hooks: pickup(spot) 返回是否成功；prompt(种类或空)；interact(种类)；pause()；look(是否锁定鼠标)
export async function createIsland(canvas, { profile, world, urls }, hooks) {
  const engine = new Engine(canvas, true, { stencil: false, preserveDrawingBuffer: false }, false)
  engine.setHardwareScalingLevel(1 / Math.min(window.devicePixelRatio || 1, 1.5))
  const scene = new Scene(engine)
  const fogDay = Color3.FromHexString('#DCEFF7')
  const fogDusk = Color3.FromHexString('#F6E2D6')
  scene.clearColor = Color4.FromColor3(fogDay)
  scene.fogMode = Scene.FOGMODE_EXP2
  scene.fogDensity = 0.0042
  scene.fogColor = fogDay.clone()
  scene.skipPointerMovePicking = true

  const hemi = new HemisphericLight('hemi', new Vector3(0.2, 1, -0.1), scene)
  hemi.intensity = 0.58
  hemi.groundColor = Color3.FromHexString('#C9B8A6')
  const sunDirection = new Vector3(-0.45, -1, 0.35).normalize()
  const sun = new DirectionalLight('sun', sunDirection, scene)
  sun.intensity = 0.65
  sun.shadowFrustumSize = 70
  sun.shadowMinZ = 1
  sun.shadowMaxZ = 160
  const shadows = new ShadowGenerator(SHADOW_SIZE, sun)
  shadows.usePercentageCloserFiltering = true
  shadows.bias = 0.002
  shadows.darkness = 0.35

  const colliders = createColliders()
  const scenery = createScenery(scene, urls, world, colliders)
  const pickups = createPickups(scene, urls, world.spots, scenery.casters)
  const effects = createEffects(scene, urls.star)

  const player = createDango(scene, urls.face, 'player')
  player.setHat(profile.hat)
  player.setOutfit(profile.outfit)
  const npc = createDango(scene, urls.face, 'shopkeeper')
  npc.setFace(Face.HAPPY)
  npc.root.position.set(SHOP.x, heightAt(SHOP.x, SHOP.z), SHOP.z)
  npc.root.rotation.y = Math.PI
  const apronMaterial = new StandardMaterial('apron', scene)
  apronMaterial.diffuseColor = Color3.FromHexString('#F7C9D6')
  const apron = MeshBuilder.CreateDisc('apron', { radius: 0.36, tessellation: 20 }, scene)
  apron.material = apronMaterial
  apron.parent = npc.root
  apron.position.set(0, 0.42, 0.57)
  apron.rotation.x = 0.35
  colliders.addCircle(SHOP.x, SHOP.z, 0.8)
  for (const m of [...player.meshes(), ...npc.meshes()]) shadows.addShadowCaster(m)
  for (const m of scenery.casters) shadows.addShadowCaster(m)
  if (profile.cleared) scenery.shrine.light()

  // 只给会发光的小东西加光晕：星之碎片、金币、灯塔的灯、神殿上的星星
  const glow = new GlowLayer('glow', scene, { blurKernelSize: 32 })
  glow.intensity = 0.55
  for (const mesh of scene.meshes)
    if (/^(star\d|shrine-star|coin$|lamp$)/.test(mesh.name)) glow.addIncludedOnlyMesh(mesh)

  // 角色状态：位置、竖直速度、朝向
  const state = {
    x: profile.x,
    z: profile.z,
    y: heightAt(profile.x, profile.z),
    vy: 0,
    yaw: profile.yaw,
    facing: profile.yaw,
    speed: 0,
    grounded: true,
    stars: profile.stars,
    cleared: profile.cleared,
    paused: false,
    prompt: null,
    chest: null,
    faceUntil: 0,
  }

  const view = createFollowCamera(scene, canvas, state, scenery.trees, {
    look: hooks.look,
    unlocked: () => {
      if (!state.paused) hooks.pause()
    },
  })

  const pressed = new Set()
  function editable(target) {
    return target instanceof HTMLElement && (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName))
  }
  function keydown(event) {
    if (editable(event.target)) return
    if (event.code === 'Escape') {
      if (!state.paused) hooks.pause()
      return
    }
    if (state.paused) return
    if (['Space', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(event.code)) event.preventDefault()
    pressed.add(event.code)
    if (event.code === 'Space' && state.grounded) {
      state.vy = Move.JUMP
      state.grounded = false
    }
    if (event.code === 'KeyE' && !event.repeat) interact()
  }
  function keyup(event) {
    pressed.delete(event.code)
  }
  function blur() {
    pressed.clear()
  }
  window.addEventListener('keydown', keydown)
  window.addEventListener('keyup', keyup)
  window.addEventListener('blur', blur)

  function react(face, seconds = 1.4) {
    player.setFace(face)
    state.faceUntil = performance.now() / 1000 + seconds
  }

  async function take(item) {
    pickups.hold(item)
    const { spot } = item
    const ok = await hooks.pickup(spot, state.x, state.z)
    pickups.settle(item, ok)
    if (!ok) return
    react(spot.kind === SpotKind.CHEST ? Face.SURPRISED : Face.HAPPY)
    effects.sparkle(spot.x, heightAt(spot.x, spot.z) + (spot.kind === SpotKind.CHEST ? 0.8 : 1), spot.z, spot.kind)
  }

  function interact() {
    if (state.prompt === Prompt.CHEST && state.chest) take(state.chest)
    else if (state.prompt) hooks.interact(state.prompt)
  }

  function setPrompt(next) {
    if (next === state.prompt) return
    state.prompt = next
    hooks.prompt(next)
  }

  const held = (names) => names.some((k) => pressed.has(k))
  let time = 0
  function step(dt) {
    time += dt
    windClock.time = time
    // 以相机朝向为前方
    const heading = view.heading()
    const fx = Math.sin(heading)
    const fz = Math.cos(heading)
    let ix = 0
    let iz = 0
    if (!state.paused) {
      const forward = (held(KEYS.forward) ? 1 : 0) - (held(KEYS.back) ? 1 : 0)
      const side = (held(KEYS.right) ? 1 : 0) - (held(KEYS.left) ? 1 : 0)
      ix = fx * forward + fz * side
      iz = fz * forward - fx * side
    }
    const len = Math.hypot(ix, iz)
    // 在浅水里走得慢
    const wading = heightAt(state.x, state.z) < 0.1
    const run = pressed.has('ShiftLeft') || pressed.has('ShiftRight')
    const target = len > 0 ? (run ? Move.RUN : Move.WALK) * (wading ? Move.WADE : 1) : 0
    state.speed += (target - state.speed) * Math.min(1, dt * 10)
    if (len > 0) {
      state.facing = Math.atan2(ix, iz)
      state.yaw = turnToward(state.yaw, state.facing, Math.min(1, dt * Move.TURN))
    }
    let x = state.x + Math.sin(state.facing) * state.speed * dt * (len > 0 ? 1 : 0.6)
    let z = state.z + Math.cos(state.facing) * state.speed * dt * (len > 0 ? 1 : 0.6)
    ;[x, z] = colliders.resolve(x, z, DANGO_RADIUS)
    const r = Math.hypot(x, z)
    if (r > ISLAND.walk) {
      x *= ISLAND.walk / r
      z *= ISLAND.walk / r
    }
    state.x = x
    state.z = z
    const ground = heightAt(x, z)
    const falling = state.vy
    state.vy -= Move.GRAVITY * dt
    state.y += state.vy * dt
    // 下坡时贴着地面走，不会一步一跳
    const wasAirborne = !state.grounded
    if (state.y <= ground || (state.grounded && state.y - ground < 0.4 && state.vy <= 0)) {
      state.y = ground
      state.vy = 0
      state.grounded = true
      if (wasAirborne && falling < -6) effects.land(x, ground, z)
    } else state.grounded = false
    effects.feet(x, ground, z, state.grounded && state.speed > Move.WALK + 1 && !wading)

    player.root.position.set(state.x, state.y, state.z)
    player.root.rotation.y = state.yaw
    player.animate(time, state.grounded ? Math.min(1, state.speed / Move.WALK) : 0, !state.grounded)
    npc.animate(time, 0, false)
    const nx = SHOP.x - state.x
    const nz = SHOP.z - state.z
    const watch = Math.hypot(nx, nz) < NPC_WATCH ? Math.atan2(-nx, -nz) : Math.PI
    npc.root.rotation.y = turnToward(npc.root.rotation.y, watch, Math.min(1, dt * 4))
    if (state.faceUntil && performance.now() / 1000 > state.faceUntil) {
      state.faceUntil = 0
      player.setFace(Face.NORMAL)
    }

    view.update(dt, state.x, state.y, state.z)
    sun.position = new Vector3(state.x, state.y, state.z).subtract(sunDirection.scale(60))

    state.chest = pickups.update(time, dt, state.x, state.y, state.z, take)
    const shop = Math.hypot(state.x - SHOP.x, state.z - SHOP.z) < SHOP_REACH
    const shrine = Math.hypot(state.x - world.shrine.x, state.z - world.shrine.z) < SHRINE_REACH
    setPrompt(state.chest ? Prompt.CHEST : shop ? Prompt.SHOP : shrine ? (state.cleared ? Prompt.SHRINE_LIT : Prompt.SHRINE) : null)

    scenery.update(time)
    const dusk = ((1 - Math.cos((time / DAY_SECONDS) * Math.PI * 2)) / 2) * 0.65
    scenery.skyMaterial.emissiveTexture.level = 1 - dusk * 0.12
    sun.diffuse = lerpColor(Color3.White(), new Color3(1, 0.84, 0.7), dusk)
    hemi.intensity = 0.58 - dusk * 0.1
    scene.fogColor = lerpColor(fogDay, fogDusk, dusk)
    scene.clearColor = Color4.FromColor3(scene.fogColor)
  }

  engine.runRenderLoop(() => {
    step(Math.min(0.05, engine.getDeltaTime() / 1000))
    scene.render()
  })
  const resize = () => engine.resize()
  window.addEventListener('resize', resize)
  await scene.whenReadyAsync()

  return {
    // 暂停时角色停下、放开鼠标，画面照常
    setPaused(paused) {
      state.paused = paused
      pressed.clear()
      if (paused) view.release()
    },
    // 继续游戏：在点击事件里调用，顺便重新锁定鼠标
    resume() {
      state.paused = false
      pressed.clear()
      view.resume()
      canvas.focus({ preventScroll: true })
    },
    setLook(hat, outfit) {
      player.setHat(hat)
      player.setOutfit(outfit)
      for (const m of player.meshes()) shadows.addShadowCaster(m)
      react(Face.HAPPY)
    },
    setStars(stars) {
      state.stars = stars
    },
    lightShrine() {
      state.cleared = true
      scenery.shrine.light()
      effects.sparkle(world.shrine.x, heightAt(world.shrine.x, world.shrine.z) + 3, world.shrine.z, SpotKind.STAR)
      react(Face.HAPPY, 3)
      state.prompt = null
      setPrompt(Prompt.SHRINE_LIT)
    },
    // 位置、朝向和相机看的方向，存档和地图用
    position() {
      return { x: state.x, y: state.y, z: state.z, yaw: state.yaw }
    },
    heading() {
      return view.heading()
    },
    focus() {
      canvas.focus({ preventScroll: true })
    },
    dispose() {
      window.removeEventListener('keydown', keydown)
      window.removeEventListener('keyup', keyup)
      window.removeEventListener('blur', blur)
      window.removeEventListener('resize', resize)
      view.dispose()
      engine.stopRenderLoop()
      scene.dispose()
      engine.dispose()
    },
  }
}
