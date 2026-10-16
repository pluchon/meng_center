// 岛上的植物。树和灌木是固定的，近处用细模型、远处用粗模型，角色走远了重新分一次；
// 草丛、花、蘑菇和小石子只在角色周围一圈生成，按 2 米一格的固定随机数决定长什么，走到哪儿长到哪儿
import { MeshBuilder } from '@babylonjs/core/Meshes/meshBuilder'
import { Mesh } from '@babylonjs/core/Meshes/mesh'
import { VertexData } from '@babylonjs/core/Meshes/mesh.vertexData'
import { Texture } from '@babylonjs/core/Materials/Textures/texture'
import { Color3 } from '@babylonjs/core/Maths/math.color'
import { Matrix, Quaternion, Vector3 } from '@babylonjs/core/Maths/math.vector'
import '@babylonjs/core/Meshes/thinInstanceMesh'
import { PLACES, SAND_LINE, SNOW_LINE, TOWERS, WORLD, fbm, heightAt, pathDistance, random, slopeAt, surfaceNear } from './terrain'
import { GIANT_TREE } from './structures'
import { toonMaterial } from './materials'
import { addWind } from './wind'

// 花瓣颜色：粉、白、黄、淡紫、浅蓝、杏
const PETALS = ['#F7B6C8', '#FFF6EC', '#F8DE7E', '#C9B8F0', '#A9D4F5', '#F9C29A'].map((hex) => Color3.FromHexString(hex))
const CAPS = ['#E66F6F', '#F3E6D2', '#F2A7BE'].map((hex) => Color3.FromHexString(hex))
const CELL = 2
// 近处树和远处树的分界
const TREE_NEAR = 130

function tiled(url, scene, scale) {
  const t = new Texture(url, scene)
  t.uScale = scale
  t.vScale = scale
  return t
}

function matrixAt(x, y, z, scale, turn, tilt = 0) {
  return Matrix.Compose(new Vector3(scale, scale, scale), Quaternion.RotationYawPitchRoll(turn, tilt, 0), new Vector3(x, y, z))
}

function merge(name, parts) {
  const mesh = Mesh.MergeMeshes(parts, true)
  mesh.name = name
  mesh.isPickable = false
  return mesh
}

function spheres(scene, name, list, segments) {
  return merge(
    name,
    list.map(([x, y, z, d], i) => {
      const s = MeshBuilder.CreateSphere(name + i, { diameter: d, segments }, scene)
      s.position.set(x, y, z)
      return s
    }),
  )
}

function cones(scene, name, list, tessellation) {
  return merge(
    name,
    list.map(([y, d, h], i) => {
      const c = MeshBuilder.CreateCylinder(name + i, { height: h * 1.4, diameterTop: 0, diameterBottom: d, tessellation }, scene)
      c.position.y = y + h * 0.7
      return c
    }),
  )
}

function trunkMesh(scene, name, height, top, bottom, tessellation) {
  const t = MeshBuilder.CreateCylinder(name, { height, diameterTop: top, diameterBottom: bottom, tessellation }, scene)
  t.position.y = height / 2
  t.bakeCurrentTransformIntoVertices()
  t.isPickable = false
  return t
}

// 把矩阵和颜色写进薄实例；空列表时不画
function fill(mesh, matrices, colors) {
  const count = matrices.length
  const buffer = new Float32Array(Math.max(1, count) * 16)
  matrices.forEach((m, i) => m.copyToArray(buffer, i * 16))
  mesh.thinInstanceSetBuffer('matrix', buffer, 16, false)
  if (colors) {
    const tint = new Float32Array(Math.max(1, count) * 4)
    colors.forEach((c, i) => tint.set([c.r, c.g, c.b, 1], i * 4))
    mesh.thinInstanceSetBuffer('color', tint, 4, false)
  }
  mesh.thinInstanceCount = count
  mesh.thinInstanceRefreshBoundingInfo(false)
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
  mesh.isPickable = false
  return mesh
}

// 低多边形小石子
function pebbleMesh(scene) {
  const rock = MeshBuilder.CreateIcoSphere('pebble', { radius: 0.5, subdivisions: 1, flat: true }, scene)
  const positions = rock.getVerticesData('position')
  const jitter = random(29)
  const offsets = new Map()
  for (let i = 0; i < positions.length; i += 3) {
    const key = positions.slice(i, i + 3).map((v) => v.toFixed(3)).join(',')
    if (!offsets.has(key)) offsets.set(key, 0.8 + jitter() * 0.4)
    const k = offsets.get(key)
    positions[i] *= k
    positions[i + 1] *= k * 0.7
    positions[i + 2] *= k
  }
  rock.setVerticesData('position', positions)
  const normals = []
  VertexData.ComputeNormals(positions, rock.getIndices(), normals)
  rock.setVerticesData('normal', normals)
  rock.isPickable = false
  return rock
}

// 某处能不能长东西：不在水里、雪里，离建筑和大石头至少 margin 米
function clear(x, z, colliders, h, margin = 0.4) {
  if (h < SAND_LINE + 0.3 || h > SNOW_LINE - 6) return false
  for (const s of colliders.near(x, z, margin + 1)) if (s.top > h + 0.3 && colliders.closest(s, x, z).gap < margin) return false
  return true
}

function createTrees(scene, urls, colliders, casters, avoid) {
  const bark = toonMaterial(scene, 'bark', '#FFFFFF', tiled(urls.bark, scene, 1))
  const leafTexture = tiled(urls.leaf, scene, 2)
  const leaf = toonMaterial(scene, 'leaf', '#FFFFFF', leafTexture)
  addWind(leaf, 0.012)
  const needles = toonMaterial(scene, 'needles', '#A3CBA8', leafTexture)
  addWind(needles, 0.01)
  const roundParts = [
    [0, 3.6, 0, 3.4],
    [0.8, 3.1, 0.3, 2.3],
    [-0.7, 3.0, -0.3, 2.1],
    [0.1, 4.7, 0, 2.1],
  ]
  const pineParts = [
    [1.6, 3.6, 2.3],
    [3.0, 2.9, 1.7],
    [4.3, 2.1, 1.2],
  ]
  const kinds = {
    round: {
      near: [trunkMesh(scene, 'trunk', 3, 0.45, 0.72, 10), spheres(scene, 'crown', roundParts, 12)],
      far: [trunkMesh(scene, 'trunk-far', 3, 0.45, 0.72, 5), spheres(scene, 'crown-far', [[0.05, 3.7, 0, 4]], 6)],
      leaves: leaf,
    },
    pine: {
      near: [trunkMesh(scene, 'pine-trunk', 2.4, 0.3, 0.5, 8), cones(scene, 'pine', pineParts, 12)],
      far: [trunkMesh(scene, 'pine-trunk-far', 2.4, 0.3, 0.5, 4), cones(scene, 'pine-far', [[1.6, 3.6, 3.6]], 6)],
      leaves: needles,
    },
  }
  for (const kind of Object.values(kinds)) {
    kind.near[0].material = bark
    kind.far[0].material = bark
    kind.near[1].material = kind.leaves
    kind.far[1].material = kind.leaves
    casters.push(...kind.near)
  }

  const trees = []
  // 5 米一格记下已经种了树的格子，相邻格子不再种
  const taken = new Set()
  const rand = random(7)
  const { mountain, plateau, village } = PLACES
  for (let tries = 0; tries < 60000 && trees.length < 1900; tries++) {
    const x = (rand() * 2 - 1) * WORLD.HALF
    const z = (rand() * 2 - 1) * WORLD.HALF
    const h = heightAt(x, z)
    if (h < SAND_LINE + 1.5 || h > SNOW_LINE - 12 || slopeAt(x, z) > 0.75 || pathDistance(x, z) < 4) continue
    const woods = surfaceNear(x, z).woods
    const onMountain = Math.hypot(x - mountain.x, z - mountain.z) < mountain.r
    const onPlateau = Math.hypot(x - plateau.x, z - plateau.z) < plateau.r
    // 森林里密，山上稀疏的松树，高原上零星几棵，别处很少
    const keep = woods > 0.3 ? 0.9 : onMountain ? 0.25 : onPlateau ? 0.12 : 0.07 + 0.08 * Math.max(0, fbm(x * 0.01, z * 0.01))
    if (rand() > keep) continue
    if (Math.hypot(x - village.x, z - village.z) < village.r + 6) continue
    if (Math.hypot(x - GIANT_TREE.x, z - GIANT_TREE.z) < 16) continue
    if (TOWERS.some((t) => Math.hypot(x - t.x, z - t.z) < 7)) continue
    if (!clear(x, z, colliders, h, 3) || avoid(x, z)) continue
    const gx = Math.floor(x / 5)
    const gz = Math.floor(z / 5)
    let crowded = false
    for (let i = -1; i <= 1; i++) for (let j = -1; j <= 1; j++) if (taken.has(gx + i + ',' + (gz + j))) crowded = true
    if (crowded) continue
    taken.add(gx + ',' + gz)
    const scale = 0.85 + rand() * 0.6
    const kind = onMountain || (woods > 0.3 && rand() < 0.45) ? 'pine' : 'round'
    const shade = 0.84 + rand() * 0.16
    trees.push({ x, z, scale, kind, m: matrixAt(x, h - 0.2, z, scale, rand() * Math.PI * 2), tint: new Color3(shade, 0.93 + (shade - 0.84) * 0.4, shade) })
    colliders.addCircle(x, z, 0.42 * scale, h - 1, h + 3 * scale)
  }

  let cx = Infinity
  let cz = Infinity
  // 角色走出 12 米就按距离重新分近处和远处
  function update(x, z) {
    if (Math.hypot(x - cx, z - cz) < 12) return
    cx = x
    cz = z
    for (const [name, kind] of Object.entries(kinds)) {
      const near = []
      const far = []
      for (const t of trees) if (t.kind === name) (Math.hypot(t.x - x, t.z - z) < TREE_NEAR ? near : far).push(t)
      fill(kind.near[0], near.map((t) => t.m))
      fill(kind.near[1], near.map((t) => t.m), near.map((t) => t.tint))
      fill(kind.far[0], far.map((t) => t.m))
      fill(kind.far[1], far.map((t) => t.m), far.map((t) => t.tint))
    }
  }
  return { trees, update }
}

function createBushes(scene, urls, colliders, casters, avoid) {
  const leaf = toonMaterial(scene, 'bush', '#C8E8B8', tiled(urls.leaf, scene, 1))
  addWind(leaf, 0.03)
  const bush = spheres(
    scene,
    'bush',
    [
      [0, 0.45, 0, 1.2],
      [0.45, 0.35, 0.15, 0.85],
      [-0.4, 0.32, -0.1, 0.8],
      [0.05, 0.38, 0.45, 0.75],
    ],
    8,
  )
  bush.material = leaf
  const rand = random(13)
  const matrices = []
  const colors = []
  for (let tries = 0; tries < 20000 && matrices.length < 520; tries++) {
    const x = (rand() * 2 - 1) * WORLD.HALF
    const z = (rand() * 2 - 1) * WORLD.HALF
    const h = heightAt(x, z)
    if (slopeAt(x, z) > 0.7 || pathDistance(x, z) < 2.5 || !clear(x, z, colliders, h, 3) || avoid(x, z)) continue
    const scale = 0.8 + rand() * 0.8
    matrices.push(matrixAt(x, h - 0.1, z, scale, rand() * Math.PI * 2))
    colors.push(new Color3(0.9 + rand() * 0.1, 1, 0.88 + rand() * 0.1))
    colliders.addCircle(x, z, 0.55 * scale, h - 0.5, h + 0.9 * scale)
  }
  fill(bush, matrices, colors)
  casters.push(bush)
}

// 角色周围的一圈花草
function createGround(scene, colliders, quality) {
  const grassMaterial = toonMaterial(scene, 'grass', '#FFFFFF')
  grassMaterial.backFaceCulling = false
  grassMaterial.specularColor = Color3.Black()
  grassMaterial.emissiveColor = new Color3(0.1, 0.12, 0.08)
  addWind(grassMaterial, 0.28, true)
  const tuft = grassTuft(scene)
  tuft.material = grassMaterial

  const green = toonMaterial(scene, 'stem', '#7CC08A')
  const petal = toonMaterial(scene, 'petal', '#FFFFFF')
  petal.emissiveColor = new Color3(0.25, 0.25, 0.25)
  const heart = toonMaterial(scene, 'heart', '#F6C94E')
  heart.emissiveColor = new Color3(0.2, 0.15, 0.02)
  for (const m of [green, petal, heart]) addWind(m, 0.14, true)
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

  const cream = toonMaterial(scene, 'mushroom-stem', '#F6EBDD')
  const capMaterial = toonMaterial(scene, 'mushroom-cap', '#FFFFFF')
  const stalk = trunkMesh(scene, 'stalk', 0.28, 0.1, 0.14, 8)
  stalk.material = cream
  const cap = MeshBuilder.CreateSphere('cap', { diameter: 0.36, slice: 0.5, segments: 10 }, scene)
  cap.scaling.y = 0.75
  cap.position.y = 0.26
  cap.bakeCurrentTransformIntoVertices()
  cap.material = capMaterial
  const pebble = pebbleMesh(scene)
  pebble.material = toonMaterial(scene, 'pebble', '#B4BEC6')

  const radius = quality.floraRadius
  const tufts = quality.tufts
  const hash = (i, j, k) => {
    let h = Math.imul(i, 73856093) ^ Math.imul(j, 19349663) ^ Math.imul(k, 83492791)
    h = Math.imul(h ^ (h >>> 13), 1274126177)
    return ((h ^ (h >>> 16)) >>> 0) / 4294967296
  }
  let cx = Infinity
  let cz = Infinity
  function update(x, z) {
    if (Math.hypot(x - cx, z - cz) < 6) return
    cx = x
    cz = z
    const grass = []
    const grassTint = []
    const flowers = []
    const petals = []
    const caps = []
    const capTint = []
    const pebbles = []
    const r = Math.ceil(radius / CELL)
    const i0 = Math.floor(x / CELL)
    const j0 = Math.floor(z / CELL)
    for (let j = j0 - r; j <= j0 + r; j++)
      for (let i = i0 - r; i <= i0 + r; i++) {
        const bx = i * CELL + CELL / 2
        const bz = j * CELL + CELL / 2
        if (Math.hypot(bx - x, bz - z) > radius) continue
        const h0 = heightAt(bx, bz)
        if (h0 < SAND_LINE + 0.3 || h0 > SNOW_LINE - 6 || slopeAt(bx, bz) > 0.95) continue
        const surface = surfaceNear(bx, bz)
        if (surface.sand > 0.4 || surface.path > 0.35) continue
        const meadow = surface.flowers
        const woods = surface.woods
        for (let k = 0; k < tufts; k++) {
          const gx = bx + (hash(i, j, k * 3) - 0.5) * CELL
          const gz = bz + (hash(i, j, k * 3 + 1) - 0.5) * CELL
          const gh = heightAt(gx, gz)
          if (!clear(gx, gz, colliders, gh)) continue
          grass.push(matrixAt(gx, gh - 0.04, gz, 0.8 + hash(i, j, k * 3 + 2) * 0.9 + meadow * 0.3, hash(i, j, k + 40) * 6.28, (hash(i, j, k + 50) - 0.5) * 0.2))
          const c = 0.85 + hash(i, j, k + 60) * 0.25 - woods * 0.1
          grassTint.push(new Color3(c, c, c * 0.95))
        }
        const roll = hash(i, j, 90)
        if (roll < 0.04 + meadow * 0.5) {
          const fx = bx + (hash(i, j, 91) - 0.5) * CELL
          const fz = bz + (hash(i, j, 92) - 0.5) * CELL
          const fh = heightAt(fx, fz)
          if (clear(fx, fz, colliders, fh)) {
            flowers.push(matrixAt(fx, fh - 0.02, fz, 1.1 + hash(i, j, 93) * 0.7, hash(i, j, 94) * 6.28, (hash(i, j, 95) - 0.5) * 0.25))
            // 同一片花色差不多：颜色按 14 米的大格子取
            const patch = Math.floor(bx / 14) * 7 + Math.floor(bz / 14) * 13
            petals.push(PETALS[Math.abs(patch + Math.floor(hash(i, j, 96) * 1.6)) % PETALS.length])
          }
        } else if (woods > 0.4 && roll > 0.95) {
          const mh = heightAt(bx, bz)
          if (clear(bx, bz, colliders, mh)) {
            caps.push(matrixAt(bx, mh - 0.02, bz, 0.7 + hash(i, j, 97) * 0.9, hash(i, j, 98) * 6.28, (hash(i, j, 99) - 0.5) * 0.3))
            capTint.push(CAPS[Math.floor(hash(i, j, 100) * CAPS.length)])
          }
        } else if (roll > 0.975) {
          const ph = heightAt(bx, bz)
          pebbles.push(matrixAt(bx, ph - 0.05, bz, 0.3 + hash(i, j, 101) * 0.6, hash(i, j, 102) * 6.28))
        }
      }
    fill(tuft, grass, grassTint)
    fill(stem, flowers)
    fill(head, flowers, petals)
    fill(center, flowers)
    fill(stalk, caps)
    fill(cap, caps, capTint)
    fill(pebble, pebbles)
  }
  return { update }
}

// spots 是地图上的点位，树和灌木不长在它们旁边；返回树的位置（相机避让用）和每帧调用的 update
export function createFlora(scene, urls, colliders, casters, quality, spots) {
  const marks = new Set(spots.filter((s) => s.y == null).map((s) => Math.floor(s.x / 4) + ',' + Math.floor(s.z / 4)))
  const avoid = (x, z) => {
    for (let i = -1; i <= 1; i++) for (let j = -1; j <= 1; j++) if (marks.has(Math.floor(x / 4) + i + ',' + (Math.floor(z / 4) + j))) return true
    return false
  }
  const trees = createTrees(scene, urls, colliders, casters, avoid)
  createBushes(scene, urls, colliders, casters, avoid)
  const ground = createGround(scene, colliders, quality)
  return {
    trees: trees.trees,
    update(x, z) {
      trees.update(x, z)
      ground.update(x, z)
    },
  }
}
