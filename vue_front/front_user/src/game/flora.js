// 岛上的植物：圆树、松树、灌木、草丛、花、蘑菇和石头，全是程序生成的立体模型，同一种用薄实例一次画完；草、花和树冠会随风摆
import { MeshBuilder } from '@babylonjs/core/Meshes/meshBuilder'
import { Mesh } from '@babylonjs/core/Meshes/mesh'
import { VertexData } from '@babylonjs/core/Meshes/mesh.vertexData'
import { StandardMaterial } from '@babylonjs/core/Materials/standardMaterial'
import { Texture } from '@babylonjs/core/Materials/Textures/texture'
import { Color3 } from '@babylonjs/core/Maths/math.color'
import { Matrix, Quaternion, Vector3 } from '@babylonjs/core/Maths/math.vector'
import '@babylonjs/core/Meshes/thinInstanceMesh'
import { HOUSES, ISLAND, PLACES, SAND_LINE, heightAt, pathDistance, random, slopeAt } from './terrain'
import { addWind } from './wind'

// 数量集中在这里，显卡吃力时先调这几个
const Count = Object.freeze({ FOREST: 110, TREES: 240, BUSHES: 130, GRASS: 9000, FLOWERS: 1300, MUSHROOMS: 110, ROCKS: 170 })

// 花瓣颜色：粉、白、黄、淡紫、浅蓝、杏
const PETALS = ['#F7B6C8', '#FFF6EC', '#F8DE7E', '#C9B8F0', '#A9D4F5', '#F9C29A'].map((hex) => Color3.FromHexString(hex))
const CAPS = ['#E66F6F', '#F3E6D2', '#F2A7BE'].map((hex) => Color3.FromHexString(hex))

function material(scene, name, hex, texture) {
  const m = new StandardMaterial(name, scene)
  m.diffuseColor = Color3.FromHexString(hex)
  m.specularColor = new Color3(0.04, 0.04, 0.04)
  if (texture) m.diffuseTexture = texture
  return m
}

function tiled(url, scene, scale) {
  const t = new Texture(url, scene)
  t.uScale = scale
  t.vScale = scale
  return t
}

function matrixAt(x, y, z, scale, turn, tilt = 0) {
  const rotation = Quaternion.RotationYawPitchRoll(turn, tilt, 0)
  return Matrix.Compose(new Vector3(scale, scale, scale), rotation, new Vector3(x, y, z))
}

// 把一组矩阵和可选的颜色交给网格做薄实例
function instance(mesh, matrices, colors) {
  if (!matrices.length) {
    mesh.dispose()
    return
  }
  const buffer = new Float32Array(matrices.length * 16)
  matrices.forEach((m, i) => m.copyToArray(buffer, i * 16))
  mesh.thinInstanceSetBuffer('matrix', buffer, 16, true)
  if (colors) {
    const tint = new Float32Array(colors.length * 4)
    colors.forEach((c, i) => tint.set([c.r, c.g, c.b, 1], i * 4))
    mesh.thinInstanceSetBuffer('color', tint, 4, true)
  }
  mesh.isPickable = false
}

function merge(name, parts) {
  const mesh = Mesh.MergeMeshes(parts, true)
  mesh.name = name
  return mesh
}

// 某处能不能种东西：不在水里、不挡房子和点位；margin 是要离小路多远
function free(x, z, blocked, margin, path = 3) {
  if (heightAt(x, z) < SAND_LINE + 0.1 || pathDistance(x, z) < path + margin) return false
  for (const b of blocked) if (Math.hypot(x - b.x, z - b.z) < b.r + margin) return false
  for (const h of HOUSES) if (Math.abs(x - h.x) < h.w / 2 + 1.5 + margin && Math.abs(z - h.z) < h.d / 2 + 1.5 + margin) return false
  return true
}

// 在岛上随机撒点，按 accept 过滤，最多 count 个
function scatter(seed, count, accept, radius = ISLAND.walk - 4, center = { x: 0, z: 0 }) {
  const rand = random(seed)
  const out = []
  for (let tries = 0; out.length < count && tries < count * 30; tries++) {
    const angle = rand() * Math.PI * 2
    const r = Math.sqrt(rand()) * radius
    const x = center.x + Math.cos(angle) * r
    const z = center.z + Math.sin(angle) * r
    if (accept(x, z, rand)) out.push({ x, z, rand })
  }
  return out
}

// 一丛草：内外两圈共十片向外弯的叶子，每片中间折一下，根部深、叶尖浅
function grassTuft(scene) {
  const positions = []
  const colors = []
  const indices = []
  const normals = []
  const base = [0.33, 0.58, 0.38]
  const mid = [0.5, 0.75, 0.48]
  const tip = [0.74, 0.92, 0.62]
  for (let i = 0; i < 10; i++) {
    const inner = i < 4
    const angle = (i / (inner ? 4 : 6)) * Math.PI * 2 + (inner ? 0.5 : 0)
    const height = inner ? 0.55 : 0.36 + (i % 3) * 0.07
    const lean = inner ? 0.1 : 0.22 + (i % 2) * 0.06
    const dx = Math.cos(angle)
    const dz = Math.sin(angle)
    const px = -dz * 0.07
    const pz = dx * 0.07
    const r0 = inner ? 0.02 : 0.08
    const start = positions.length / 3
    positions.push(
      dx * r0 - px, 0, dz * r0 - pz,
      dx * r0 + px, 0, dz * r0 + pz,
      dx * lean * 0.45 - px * 0.6, height * 0.55, dz * lean * 0.45 - pz * 0.6,
      dx * lean * 0.45 + px * 0.6, height * 0.55, dz * lean * 0.45 + pz * 0.6,
      dx * lean, height, dz * lean,
    )
    for (const c of [base, base, mid, mid, tip]) colors.push(...c, 1)
    for (let k = 0; k < 5; k++) normals.push(dx * 0.3, 1, dz * 0.3)
    indices.push(start, start + 1, start + 2, start + 1, start + 3, start + 2, start + 2, start + 3, start + 4)
  }
  const data = new VertexData()
  Object.assign(data, { positions, colors, indices, normals })
  const mesh = new Mesh('grass', scene)
  data.applyToMesh(mesh)
  return mesh
}

function createTrees(scene, urls, blocked, colliders, casters) {
  const bark = material(scene, 'bark', '#FFFFFF', tiled(urls.bark, scene, 1))
  const leafTexture = tiled(urls.leaf, scene, 2)
  const leaf = material(scene, 'leaf', '#FFFFFF', leafTexture)
  addWind(leaf, 0.025)
  const needles = material(scene, 'needles', '#9CC9A4', leafTexture)
  addWind(needles, 0.02)

  // 圆树：粗树干加几团球
  const trunk = MeshBuilder.CreateCylinder('trunk', { height: 3, diameterTop: 0.45, diameterBottom: 0.7, tessellation: 10 }, scene)
  trunk.position.y = 1.5
  trunk.bakeCurrentTransformIntoVertices()
  trunk.material = bark
  const crown = merge(
    'crown',
    [
      [0, 3.6, 0, 3.2],
      [0.7, 3.1, 0.3, 2.2],
      [-0.6, 3.0, -0.3, 2.0],
      [0.1, 4.6, 0, 2.0],
    ].map(([x, y, z, d], i) => {
      const s = MeshBuilder.CreateSphere('crown' + i, { diameter: d, segments: 12 }, scene)
      s.position.set(x, y, z)
      return s
    }),
  )
  crown.material = leaf

  // 松树：细高的树干叠三层圆锥
  const pineTrunk = MeshBuilder.CreateCylinder('pine-trunk', { height: 2.4, diameterTop: 0.3, diameterBottom: 0.5, tessellation: 8 }, scene)
  pineTrunk.position.y = 1.2
  pineTrunk.bakeCurrentTransformIntoVertices()
  pineTrunk.material = bark
  const pine = merge(
    'pine',
    [
      [1.7, 3.4, 2.2],
      [3.0, 2.8, 1.6],
      [4.2, 2.1, 1.1],
    ].map(([y, d, h], i) => {
      const cone = MeshBuilder.CreateCylinder('pine' + i, { height: h * 1.4, diameterTop: 0, diameterBottom: d, tessellation: 12 }, scene)
      cone.position.y = y + h * 0.7
      return cone
    }),
  )
  pine.material = needles

  const { forest } = PLACES
  const trees = []
  const place = (x, z, rand, inForest) => {
    if (trees.length >= (inForest ? Count.FOREST : Count.TREES)) return false
    if (!free(x, z, blocked, 1.2) || slopeAt(x, z) > 0.75) return false
    if (trees.some((t) => Math.hypot(t.x - x, t.z - z) < 3.3)) return false
    const scale = 0.8 + rand() * 0.55
    const kind = inForest && rand() < 0.5 ? 'pine' : 'round'
    trees.push({ x, z, scale, kind, m: matrixAt(x, heightAt(x, z) - 0.2, z, scale, rand() * Math.PI * 2) })
    colliders.addCircle(x, z, 0.42 * scale)
    return false
  }
  scatter(7, Count.TREES, (x, z, rand) => place(x, z, rand, true), forest.r, forest)
  scatter(9, Count.TREES, (x, z, rand) => Math.hypot(x - forest.x, z - forest.z) > forest.r && rand() < 0.45 && place(x, z, rand, false), ISLAND.walk - 10)

  const shade = random(41)
  const tints = (list) => list.map(() => {
    const c = 0.84 + shade() * 0.16
    return new Color3(c, 0.93 + (c - 0.84) * 0.4, c)
  })
  const round = trees.filter((t) => t.kind === 'round')
  const pines = trees.filter((t) => t.kind === 'pine')
  instance(trunk, round.map((t) => t.m))
  instance(crown, round.map((t) => t.m), tints(round))
  instance(pineTrunk, pines.map((t) => t.m))
  instance(pine, pines.map((t) => t.m), tints(pines))
  casters.push(trunk, crown, pineTrunk, pine)
  return trees
}

function createBushes(scene, urls, blocked, colliders, casters) {
  const leaf = material(scene, 'bush', '#C8E8B8', tiled(urls.leaf, scene, 1))
  addWind(leaf, 0.04)
  const bush = merge(
    'bush',
    [
      [0, 0.45, 0, 1.2],
      [0.45, 0.35, 0.15, 0.85],
      [-0.4, 0.32, -0.1, 0.8],
      [0.05, 0.38, 0.45, 0.75],
    ].map(([x, y, z, d], i) => {
      const s = MeshBuilder.CreateSphere('bush' + i, { diameter: d, segments: 8 }, scene)
      s.position.set(x, y, z)
      return s
    }),
  )
  bush.material = leaf
  const spots = scatter(13, Count.BUSHES, (x, z) => free(x, z, blocked, 0.6, 2))
  const matrices = spots.map(({ x, z, rand }) => {
    const scale = 0.8 + rand() * 0.7
    colliders.addCircle(x, z, 0.55 * scale)
    return matrixAt(x, heightAt(x, z) - 0.1, z, scale, rand() * Math.PI * 2)
  })
  instance(bush, matrices, spots.map(({ rand }) => new Color3(0.9 + rand() * 0.1, 1, 0.9 + rand() * 0.1)))
  casters.push(bush)
}

function createGrass(scene, blocked) {
  const m = material(scene, 'grass', '#FFFFFF')
  m.backFaceCulling = false
  m.specularColor = Color3.Black()
  m.emissiveColor = new Color3(0.12, 0.14, 0.1)
  addWind(m, 0.28)
  const tuft = grassTuft(scene)
  tuft.material = m
  const ok = (x, z) => heightAt(x, z) > SAND_LINE + 0.15 && pathDistance(x, z) > 1.6 && !blocked.some((b) => Math.hypot(x - b.x, z - b.z) < Math.min(b.r, 1.6))
  // 草一片一片地长：先挑片心，再在周围撒
  const spots = []
  for (const { x, z, rand } of scatter(17, Count.GRASS / 6, ok))
    for (let i = 0; i < 6; i++) {
      const gx = x + (rand() - 0.5) * 3.2
      const gz = z + (rand() - 0.5) * 3.2
      if (ok(gx, gz)) spots.push({ x: gx, z: gz, rand })
    }
  instance(
    tuft,
    spots.map(({ x, z, rand }) => matrixAt(x, heightAt(x, z) - 0.04, z, 0.8 + rand() * 0.9, rand() * Math.PI * 2, (rand() - 0.5) * 0.2)),
    spots.map(({ rand }) => {
      const c = 0.85 + rand() * 0.25
      return new Color3(c, c, c * 0.95)
    }),
  )
}

// 花：花茎带两片叶子、五片花瓣、黄色花心，三部分共用同一组矩阵
function createFlowers(scene, blocked) {
  const green = material(scene, 'stem', '#7CC08A')
  const petal = material(scene, 'petal', '#FFFFFF')
  petal.emissiveColor = new Color3(0.28, 0.28, 0.28)
  const heart = material(scene, 'heart', '#F6C94E')
  heart.emissiveColor = new Color3(0.2, 0.15, 0.02)
  for (const m of [green, petal, heart]) addWind(m, 0.14)

  const stemParts = [MeshBuilder.CreateCylinder('stem', { height: 0.5, diameter: 0.04, tessellation: 5 }, scene)]
  stemParts[0].position.y = 0.25
  for (const side of [-1, 1]) {
    const blade = MeshBuilder.CreateSphere('leaf', { diameter: 0.18, segments: 5 }, scene)
    blade.scaling.set(1, 0.25, 0.45)
    blade.position.set(side * 0.08, 0.14 + side * 0.03, 0)
    blade.rotation.z = side * -0.5
    stemParts.push(blade)
  }
  const stem = merge('stems', stemParts)
  stem.material = green
  const head = merge(
    'petals',
    [0, 1, 2, 3, 4].map((i) => {
      const angle = (i / 5) * Math.PI * 2
      const p = MeshBuilder.CreateSphere('petal' + i, { diameter: 0.22, segments: 6 }, scene)
      p.scaling.set(1, 0.3, 0.6)
      p.rotation.set(0, -angle, 0.5)
      p.position.set(Math.cos(angle) * 0.1, 0.53, Math.sin(angle) * 0.1)
      return p
    }),
  )
  head.material = petal
  const center = MeshBuilder.CreateSphere('heart', { diameter: 0.1, segments: 6 }, scene)
  center.position.y = 0.54
  center.bakeCurrentTransformIntoVertices()
  center.material = heart

  // 花一簇一簇地开：先选簇心，再在周围撒几朵同色的
  const clusters = scatter(19, Count.FLOWERS / 5, (x, z) => free(x, z, blocked, 0, 1.2))
  const matrices = []
  const colors = []
  for (const { x, z, rand } of clusters) {
    const color = PETALS[Math.floor(rand() * PETALS.length)]
    for (let i = 0; i < 5; i++) {
      const fx = x + (rand() - 0.5) * 2.4
      const fz = z + (rand() - 0.5) * 2.4
      if (heightAt(fx, fz) < SAND_LINE + 0.2 || pathDistance(fx, fz) < 1.2) continue
      matrices.push(matrixAt(fx, heightAt(fx, fz) - 0.02, fz, 1.1 + rand() * 0.7, rand() * Math.PI * 2, (rand() - 0.5) * 0.25))
      colors.push(color)
    }
  }
  instance(stem, matrices)
  instance(head, matrices, colors)
  instance(center, matrices)
}

// 蘑菇只长在森林里和树荫下
function createMushrooms(scene, blocked, trees) {
  const cream = material(scene, 'mushroom-stem', '#F6EBDD')
  const cap = material(scene, 'mushroom-cap', '#FFFFFF')
  cap.emissiveColor = new Color3(0.08, 0.06, 0.06)
  const stalk = MeshBuilder.CreateCylinder('stalk', { height: 0.28, diameterTop: 0.1, diameterBottom: 0.14, tessellation: 8 }, scene)
  stalk.position.y = 0.14
  stalk.bakeCurrentTransformIntoVertices()
  stalk.material = cream
  const top = MeshBuilder.CreateSphere('cap', { diameter: 0.36, slice: 0.5, segments: 10 }, scene)
  top.scaling.y = 0.75
  top.position.y = 0.26
  top.bakeCurrentTransformIntoVertices()
  top.material = cap
  const dots = merge(
    'dots',
    [
      [0.08, 0.36, 0.05],
      [-0.07, 0.35, 0.07],
      [0.0, 0.39, -0.08],
      [-0.1, 0.31, -0.06],
    ].map(([x, y, z], i) => {
      const d = MeshBuilder.CreateSphere('dot' + i, { diameter: 0.05, segments: 4 }, scene)
      d.position.set(x, y, z)
      return d
    }),
  )
  dots.material = material(scene, 'dots', '#FFFFFF')
  const near = trees.filter((t) => t.kind === 'pine' || Math.hypot(t.x - PLACES.forest.x, t.z - PLACES.forest.z) < PLACES.forest.r + 6)
  const rand = random(23)
  const matrices = []
  const colors = []
  for (let i = 0; i < Count.MUSHROOMS && near.length; i++) {
    const t = near[Math.floor(rand() * near.length)]
    const angle = rand() * Math.PI * 2
    const x = t.x + Math.cos(angle) * (1 + rand() * 1.6)
    const z = t.z + Math.sin(angle) * (1 + rand() * 1.6)
    if (!free(x, z, blocked, 0, 1)) continue
    for (let k = 0; k < 1 + Math.floor(rand() * 3); k++) {
      const mx = x + (rand() - 0.5) * 0.7
      const mz = z + (rand() - 0.5) * 0.7
      matrices.push(matrixAt(mx, heightAt(mx, mz) - 0.02, mz, 0.7 + rand() * 0.9, rand() * Math.PI * 2, (rand() - 0.5) * 0.3))
      colors.push(CAPS[Math.floor(rand() * CAPS.length)])
    }
  }
  instance(stalk, matrices)
  instance(top, matrices, colors)
  instance(dots, matrices)
}

// 石头：抖过顶点的二十面体，平直着色出低多边形的样子
function createRocks(scene, blocked, colliders) {
  const rock = MeshBuilder.CreateIcoSphere('rock', { radius: 0.5, subdivisions: 1, flat: true }, scene)
  const positions = rock.getVerticesData('position')
  const jitter = random(29)
  const offsets = new Map()
  for (let i = 0; i < positions.length; i += 3) {
    // 同一个顶点在平直着色时会出现在多个面上，按坐标记住偏移，保证面和面不裂开
    const key = positions.slice(i, i + 3).map((v) => v.toFixed(3)).join(',')
    if (!offsets.has(key)) offsets.set(key, 0.8 + jitter() * 0.4)
    const k = offsets.get(key)
    positions[i] *= k
    positions[i + 1] *= k * 0.72
    positions[i + 2] *= k
  }
  rock.setVerticesData('position', positions)
  const normals = []
  VertexData.ComputeNormals(positions, rock.getIndices(), normals)
  rock.setVerticesData('normal', normals)
  rock.refreshBoundingInfo()
  rock.material = material(scene, 'rock', '#B4BEC6')
  const spots = scatter(31, Count.ROCKS, (x, z, rand) => free(x, z, blocked, 0, 1.4) || (heightAt(x, z) > 0.2 && heightAt(x, z) < SAND_LINE && rand() < 0.4))
  const matrices = spots.map(({ x, z, rand }) => {
    const scale = 0.4 + rand() * rand() * 1.8
    if (scale > 1.1) colliders.addCircle(x, z, 0.4 * scale)
    return matrixAt(x, heightAt(x, z) - 0.08 * scale, z, scale, rand() * Math.PI * 2, (rand() - 0.5) * 0.3)
  })
  instance(rock, matrices, spots.map(({ rand }) => {
    const c = 0.85 + rand() * 0.2
    return new Color3(c, c, c * (0.95 + rand() * 0.1))
  }))
  rock.receiveShadows = true
}

// blocked 是点位和地标附近不种东西的圆；返回树的位置，相机避让要用
export function createFlora(scene, urls, blocked, colliders, casters) {
  const trees = createTrees(scene, urls, blocked, colliders, casters)
  createBushes(scene, urls, blocked, colliders, casters)
  createGrass(scene, blocked)
  createFlowers(scene, blocked)
  createMushrooms(scene, blocked, trees)
  createRocks(scene, blocked, colliders)
  return trees
}
