// 萌岛的 3D 世界：引擎、光照和昼夜、键盘、角色、相机和每帧更新。只认普通对象，不进 Vue 的响应式
import { Engine } from '@babylonjs/core/Engines/engine'
import { Scene } from '@babylonjs/core/scene'
import { HemisphericLight } from '@babylonjs/core/Lights/hemisphericLight'
import { DirectionalLight } from '@babylonjs/core/Lights/directionalLight'
import { ShadowGenerator } from '@babylonjs/core/Lights/Shadows/shadowGenerator'
import '@babylonjs/core/Lights/Shadows/shadowGeneratorSceneComponent'
import { GlowLayer } from '@babylonjs/core/Layers/glowLayer'
import '@babylonjs/core/Layers/effectLayerSceneComponent'
import { MeshBuilder } from '@babylonjs/core/Meshes/meshBuilder'
import { Color3, Color4 } from '@babylonjs/core/Maths/math.color'
import { Matrix, Vector3 } from '@babylonjs/core/Maths/math.vector'
import { Prompt, SpotKind } from '@/constants/game'
import { PLACES, SHOP, SNOW_LINE, WATER, coastAt, heightAt, regionAt, slopeAt, surfaceNear } from './terrain'
import { createColliders } from './collision'
import { createGround, createHeightTexture } from './ground'
import { createWater } from './water'
import { DAY_SECONDS, START_HOUR, createSky, skyAt } from './sky'
import { createStructures } from './structures'
import { createFlora } from './flora'
import { createPickups } from './pickups'
import { createEffects } from './effects'
import { createFollowCamera } from './camera'
import { createPost } from './post'
import { createPlayer, Mode } from './player'
import { toonMaterial, toonState } from './materials'
import { windClock } from './wind'
import { Face, createDango } from './dango'

const SHOP_REACH = 3.4
const SHRINE_REACH = 7
// 团子老板在这个距离内会转过来看着你
const NPC_WATCH = 10
const FOG = 0.0016

const KEYS = Object.freeze({
  forward: ['KeyW', 'ArrowUp'],
  back: ['KeyS', 'ArrowDown'],
  left: ['KeyA', 'ArrowLeft'],
  right: ['KeyD', 'ArrowRight'],
})

function turnToward(from, to, k) {
  return from + Math.atan2(Math.sin(to - from), Math.cos(to - from)) * k
}

// 脚下是什么地面，挑脚步声用
function groundKind(mode, x, y, z) {
  if (mode === Mode.SWIM || y < WATER + 0.15) return 'sand'
  if (y > SNOW_LINE) return 'snow'
  if (y - heightAt(x, z) > 0.3) return 'wood'
  const s = surfaceNear(x, z)
  if (s.path > 0.5) return 'path'
  if (s.sand > 0.5) return 'sand'
  return slopeAt(x, z) > 0.8 ? 'rock' : 'grass'
}

// 存档的位置在水里或岛外时（比如旧版本的存档），从出生的海滩开始
function startOf(profile) {
  const inside = Math.hypot(profile.x, profile.z) < coastAt(Math.atan2(profile.z, profile.x)) - 20
  if (inside && heightAt(profile.x, profile.z) > WATER + 0.5) return { x: profile.x, y: profile.y, z: profile.z, yaw: profile.yaw }
  return { x: PLACES.spawn.x, y: heightAt(PLACES.spawn.x, PLACES.spawn.z), z: PLACES.spawn.z, yaw: 0 }
}

// hooks: pickup(spot) 返回是否成功；prompt(种类或空)；interact(种类)；pause()；look(是否锁定鼠标)；notice(提示键)
export async function createIsland(canvas, { profile, world, urls, quality, audio }, hooks) {
  const engine = new Engine(canvas, true, { stencil: false, preserveDrawingBuffer: false }, false)
  engine.setHardwareScalingLevel(quality.scale / Math.min(window.devicePixelRatio || 1, 1.5))
  const scene = new Scene(engine)
  scene.fogMode = Scene.FOGMODE_EXP2
  scene.fogDensity = FOG
  scene.skipPointerMovePicking = true

  const hemi = new HemisphericLight('hemi', new Vector3(0.2, 1, -0.1), scene)
  const sun = new DirectionalLight('sun', new Vector3(-0.4, -1, 0.3), scene)
  sun.shadowFrustumSize = 90
  sun.shadowMinZ = 1
  sun.shadowMaxZ = 260
  const shadows = new ShadowGenerator(quality.shadow, sun)
  shadows.usePercentageCloserFiltering = true
  shadows.bias = 0.0015
  shadows.darkness = 0.3

  const colliders = createColliders()
  const casters = []
  const ground = createGround(scene, urls)
  const height = createHeightTexture(scene)
  const updateWater = createWater(scene, height.texture, height.size)
  const updateSky = createSky(scene)
  const effects = createEffects(scene, urls.star)
  const structures = createStructures(scene, urls, world, colliders, casters, effects.dot)
  const flora = createFlora(scene, urls, colliders, casters, quality, world.spots)
  const pickups = createPickups(scene, urls, world.spots, casters)

  const look = createDango(scene, urls.face, 'player')
  look.setHat(profile.hat)
  look.setOutfit(profile.outfit)
  const npc = createDango(scene, urls.face, 'shopkeeper')
  npc.setFace(Face.HAPPY)
  npc.root.position.set(SHOP.x, heightAt(SHOP.x, SHOP.z), SHOP.z)
  npc.root.rotation.y = Math.PI
  const apron = MeshBuilder.CreateDisc('apron', { radius: 0.36, tessellation: 20 }, scene)
  apron.material = toonMaterial(scene, 'apron', '#F7C9D6')
  apron.parent = npc.root
  apron.position.set(0, 0.42, 0.57)
  apron.rotation.x = 0.35
  colliders.addCircle(SHOP.x, SHOP.z, 0.8, heightAt(SHOP.x, SHOP.z), heightAt(SHOP.x, SHOP.z) + 1.2)
  for (const m of [...look.meshes(), ...npc.meshes(), ...casters]) shadows.addShadowCaster(m)
  if (profile.cleared) structures.shrine.light()

  // 只给会发光的小东西加光晕：星之碎片、金币、灯塔的灯、神殿上的星星
  const glow = new GlowLayer('glow', scene, { blurKernelSize: 32 })
  glow.intensity = 0.6
  for (const mesh of scene.meshes) if (/^(star\d|shrine-star|coin$|lamp$)/.test(mesh.name)) glow.addIncludedOnlyMesh(mesh)

  const state = { paused: false, prompt: null, chest: null, faceUntil: 0, cleared: profile.cleared, stars: profile.stars }
  const player = createPlayer(startOf(profile), colliders, {
    step: (x, z, loud, fast) => audio?.step(groundKind(player.state.mode, x, player.state.y, z), loud || fast),
    land: (x, y, z, speed) => {
      effects.land(x, y, z)
      audio?.land(-speed)
      react(Face.SURPRISED, 0.6)
    },
    jump: () => audio?.jump(),
    glide: (open) => audio?.glide(open),
    splash: (x, z) => {
      audio?.splash()
      effects.splash(x, WATER, z)
    },
    grab: () => audio?.grab(),
    exhausted: () => {
      audio?.exhausted()
      react(Face.SURPRISED, 1.2)
    },
    drown: () => hooks.notice('drowned'),
  })
  const p = player.state

  const view = createFollowCamera(scene, canvas, p, flora.trees, colliders, {
    look: hooks.look,
    unlocked: () => {
      if (!state.paused) hooks.pause()
    },
  })
  const post = createPost(scene, view.camera, quality)
  // 水面也写进深度图，水下的东西就不会透出描边
  const outline = () => post.outline([...ground.chunks, scene.getMeshByName('water'), ...casters, ...look.meshes(), ...npc.meshes()])
  outline()

  const pressed = new Set()
  let jumpQueued = false
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
    if (event.code === 'Space' && !event.repeat) jumpQueued = true
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
    look.setFace(face)
    state.faceUntil = performance.now() / 1000 + seconds
  }

  async function take(item) {
    pickups.hold(item)
    const { spot } = item
    const ok = await hooks.pickup(spot, p.x, p.z)
    pickups.settle(item, ok)
    if (!ok) return
    react(spot.kind === SpotKind.CHEST ? Face.SURPRISED : Face.HAPPY)
    const y = (spot.y ?? heightAt(spot.x, spot.z)) + (spot.kind === SpotKind.CHEST ? 0.8 : 1)
    effects.sparkle(spot.x, y, spot.z, spot.kind)
    if (spot.kind === SpotKind.STAR) audio?.star()
    else if (spot.kind === SpotKind.CHEST) audio?.chest()
    else audio?.coin()
  }

  function interact() {
    if (state.prompt === Prompt.CHEST && state.chest) take(state.chest)
    else if (state.prompt) {
      if (state.prompt === Prompt.SHOP) audio?.talk()
      hooks.interact(state.prompt)
    }
  }

  function setPrompt(next) {
    if (next === state.prompt) return
    state.prompt = next
    hooks.prompt(next)
  }

  const held = (names) => names.some((k) => pressed.has(k))
  let time = 0
  let hour = START_HOUR
  function step(dt) {
    time += dt
    hour = (START_HOUR + (time / DAY_SECONDS) * 24) % 24
    const input = state.paused
      ? { forward: 0, side: 0, sprint: false, jump: false }
      : {
          forward: (held(KEYS.forward) ? 1 : 0) - (held(KEYS.back) ? 1 : 0),
          side: (held(KEYS.right) ? 1 : 0) - (held(KEYS.left) ? 1 : 0),
          sprint: pressed.has('ShiftLeft') || pressed.has('ShiftRight'),
          jump: jumpQueued,
        }
    jumpQueued = false
    player.update(dt, input, view.heading())

    const pose = player.pose()
    const offset = player.renderOffset()
    look.root.position.set(p.x + offset.x, p.y, p.z + offset.z)
    look.root.rotation.y = p.yaw
    look.pose(pose.kind, time, pose.k)
    npc.animate(time, 0, false)
    const nx = SHOP.x - p.x
    const nz = SHOP.z - p.z
    const watch = Math.hypot(nx, nz) < NPC_WATCH ? Math.atan2(-nx, -nz) : Math.PI
    npc.root.rotation.y = turnToward(npc.root.rotation.y, watch, Math.min(1, dt * 4))
    if (state.faceUntil && performance.now() / 1000 > state.faceUntil) {
      state.faceUntil = 0
      look.setFace(Face.NORMAL)
    }
    effects.feet(p.x, p.y, p.z, p.mode === Mode.GROUND && p.poseSpeed > 8)

    view.update(dt, p.x, p.y, p.z)
    flora.update(p.x, p.z)
    windClock.time = time
    windClock.x = p.x
    windClock.y = p.y
    windClock.z = p.z
    windClock.cx = p.x
    windClock.cz = p.z
    windClock.radius = quality.floraRadius

    state.chest = pickups.update(time, dt, p.x, p.y, p.z, take)
    const shop = Math.hypot(p.x - SHOP.x, p.z - SHOP.z) < SHOP_REACH
    const shrine = Math.hypot(p.x - world.shrine.x, p.z - world.shrine.z) < SHRINE_REACH && p.y > 100
    setPrompt(state.chest ? Prompt.CHEST : shop ? Prompt.SHOP : shrine ? (state.cleared ? Prompt.SHRINE_LIT : Prompt.SHRINE) : null)

    // 昼夜：太阳、月光、天空、雾和卡通明暗的光量一起变
    const sky = skyAt(hour)
    sun.direction = sky.lightDir
    sun.diffuse = sky.sunColor
    sun.intensity = sky.sunIntensity
    sun.position = new Vector3(p.x, p.y, p.z).subtract(sky.lightDir.scale(120))
    hemi.intensity = sky.hemiIntensity
    hemi.diffuse = Color3.Lerp(sky.horizon, sky.zenith, 0.4).add(new Color3(0.25, 0.25, 0.25))
    hemi.groundColor = sky.fog.scale(0.7)
    toonState.light = sky.sunIntensity + sky.hemiIntensity
    scene.fogColor = sky.fog
    scene.clearColor = Color4.FromColor3(sky.fog)
    updateSky(sky, time)
    updateWater(sky, time, view.camera, FOG, Math.min(1, 0.35 + toonState.light * 0.6))
    structures.shrine.update(time)
    structures.setNight(sky.night)
    effects.night(sky.night, p.x, p.y, p.z)

    audio?.update({
      altitude: Math.max(0, p.y - heightAt(p.x, p.z)) + Math.max(0, p.y - 40) * 0.5,
      speed: Math.hypot(p.vx, p.vz) + (p.mode === Mode.GLIDE ? 6 : 0),
      nearSea: Math.max(0, 1 - Math.abs(Math.hypot(p.x, p.z) - coastAt(Math.atan2(p.z, p.x))) / 60),
      night: sky.night,
      region: regionAt(p.x, p.z),
      paused: state.paused,
    })
  }

  engine.runRenderLoop(() => {
    step(Math.min(0.05, engine.getDeltaTime() / 1000))
    scene.render()
  })
  const resize = () => engine.resize()
  window.addEventListener('resize', resize)
  await scene.whenReadyAsync()

  const projected = new Vector3()
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
      audio?.resume()
      canvas.focus({ preventScroll: true })
    },
    setLook(hat, outfit) {
      look.setHat(hat)
      look.setOutfit(outfit)
      for (const m of look.meshes()) shadows.addShadowCaster(m)
      outline()
      react(Face.HAPPY)
    },
    setStars(stars) {
      state.stars = stars
    },
    lightShrine() {
      state.cleared = true
      structures.shrine.light()
      effects.sparkle(world.shrine.x, p.y + 3, world.shrine.z, SpotKind.STAR)
      audio?.shrine()
      react(Face.HAPPY, 3)
      state.prompt = null
      setPrompt(Prompt.SHRINE_LIT)
    },
    // 位置、朝向和相机看的方向，存档和地图用
    position() {
      return { x: p.x, y: p.y, z: p.z, yaw: p.yaw }
    },
    heading() {
      return view.heading()
    },
    // 耐力环要的数据：耐力、是否透支、角色头顶在屏幕上的位置（0 到 1）
    stamina() {
      const at = new Vector3(p.x, p.y + 1.6, p.z)
      const w = engine.getRenderWidth()
      const h = engine.getRenderHeight()
      Vector3.ProjectToRef(at, Matrix.IdentityReadOnly, scene.getTransformMatrix(), view.camera.viewport.toGlobal(w, h), projected)
      return { value: p.stamina, max: p.max, exhausted: p.exhausted, x: projected.x / w, y: projected.y / h }
    },
    clock() {
      return hour
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
