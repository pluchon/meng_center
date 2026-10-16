// 岛上的建筑和大件：小村、商店摊位、海崖灯塔、瞭望台、大石头、巨树、浮空岛和岛上的神殿、上升气流
import { MeshBuilder } from '@babylonjs/core/Meshes/meshBuilder'
import { Mesh } from '@babylonjs/core/Meshes/mesh'
import { VertexData } from '@babylonjs/core/Meshes/mesh.vertexData'
import { TransformNode } from '@babylonjs/core/Meshes/transformNode'
import { Texture } from '@babylonjs/core/Materials/Textures/texture'
import { ParticleSystem } from '@babylonjs/core/Particles/particleSystem'
import '@babylonjs/core/Particles/particleSystemComponent'
import { Color3, Color4 } from '@babylonjs/core/Maths/math.color'
import { Matrix, Quaternion, Vector3 } from '@babylonjs/core/Maths/math.vector'
import { HOUSES, PLACES, SHOP, SKY_ISLANDS, TOWERS, UPDRAFTS, heightAt, pathDistance, random, slopeAt } from './terrain'
import { toonMaterial } from './materials'

// 巨树：长在高原上，树干能爬
export const GIANT_TREE = Object.freeze({ x: -185, z: 250, r: 3.6, height: 38 })

function tiled(url, scene, scale) {
  const t = new Texture(url, scene)
  t.uScale = scale
  t.vScale = scale
  return t
}

// 双坡屋顶：屋脊沿 x 方向，底面宽 w、深 d、高 h
function gableRoof(scene, name, w, d, h) {
  const x = w / 2
  const z = d / 2
  const faces = [
    [[-x, 0, -z], [-x, h, 0], [x, h, 0], [x, 0, -z]],
    [[x, 0, z], [x, h, 0], [-x, h, 0], [-x, 0, z]],
    [[-x, 0, z], [-x, h, 0], [-x, 0, -z]],
    [[x, 0, -z], [x, h, 0], [x, 0, z]],
  ]
  const positions = []
  const indices = []
  const uvs = []
  for (const f of faces) {
    const start = positions.length / 3
    f.forEach(([px, py, pz]) => {
      positions.push(px, py, pz)
      uvs.push(f.length === 4 ? (px + x) / 2 : (pz + z) / 2, py / 1.2)
    })
    indices.push(start, start + 2, start + 1)
    if (f.length === 4) indices.push(start, start + 3, start + 2)
  }
  const normals = []
  VertexData.ComputeNormals(positions, indices, normals)
  const data = new VertexData()
  Object.assign(data, { positions, indices, normals, uvs })
  const mesh = new Mesh(name, scene)
  data.applyToMesh(mesh)
  return mesh
}

// 抖过顶点的低多边形石块，半径 1
function boulderMesh(scene, name, seed) {
  const rock = MeshBuilder.CreateIcoSphere(name, { radius: 1, subdivisions: 2, flat: true }, scene)
  const positions = rock.getVerticesData('position')
  const jitter = random(seed)
  const offsets = new Map()
  for (let i = 0; i < positions.length; i += 3) {
    const key = positions.slice(i, i + 3).map((v) => v.toFixed(3)).join(',')
    if (!offsets.has(key)) offsets.set(key, 0.82 + jitter() * 0.3)
    const k = offsets.get(key)
    positions[i] *= k
    positions[i + 1] *= k * 0.8
    positions[i + 2] *= k
  }
  rock.setVerticesData('position', positions)
  const normals = []
  VertexData.ComputeNormals(positions, rock.getIndices(), normals)
  rock.setVerticesData('normal', normals)
  rock.refreshBoundingInfo()
  return rock
}

function createVillage(scene, urls, mats, colliders, casters) {
  const roof = toonMaterial(scene, 'roof', '#FFFFFF', tiled(urls.roof, scene, 2))
  roof.backFaceCulling = false
  const door = toonMaterial(scene, 'door', '#C99C79')
  for (const [i, h] of HOUSES.entries()) {
    const base = heightAt(h.x, h.z) - 0.8
    const house = new TransformNode('house' + i, scene)
    house.position.set(h.x, base, h.z)
    const body = MeshBuilder.CreateBox('wall' + i, { width: h.w, height: h.h + 0.8, depth: h.d }, scene)
    body.position.y = (h.h + 0.8) / 2
    body.material = mats.wall
    body.parent = house
    const top = gableRoof(scene, 'roof' + i, h.w + 1, h.d + 1, 2.8)
    top.position.y = h.h + 0.8
    top.material = roof
    top.parent = house
    // 门窗朝向村子中心
    const facing = Math.atan2(PLACES.village.x - h.x, PLACES.village.z - h.z)
    const front = new TransformNode('front' + i, scene)
    front.parent = house
    front.position.y = 0.8
    front.rotation.y = facing
    const depth = Math.abs(Math.sin(facing)) > Math.SQRT1_2 ? h.w : h.d
    const doorMesh = MeshBuilder.CreateBox('door' + i, { width: 1.3, height: 2.2, depth: 0.14 }, scene)
    doorMesh.position.set(0, 1.1, depth / 2 + 0.04)
    doorMesh.material = door
    doorMesh.parent = front
    for (const side of [-1, 1]) {
      const pane = MeshBuilder.CreateBox('window' + i + side, { width: 1, height: 0.9, depth: 0.12 }, scene)
      pane.position.set(side * 2.2, 2.2, depth / 2 + 0.04)
      pane.material = mats.glass
      pane.parent = front
    }
    colliders.addBox(h.x, h.z, h.w, h.d, base, base + h.h + 1.6, true)
    casters.push(body, top)
  }
  // 摊位：团子身后，四根柱子撑一块粉色顶棚
  const stall = new TransformNode('stall', scene)
  const sx = SHOP.x
  const sz = SHOP.z + 2.2
  const sy = heightAt(sx, sz)
  stall.position.set(sx, sy, sz)
  const counter = MeshBuilder.CreateBox('counter', { width: 3.6, height: 1, depth: 1 }, scene)
  counter.position.y = 0.5
  counter.material = door
  counter.parent = stall
  const canopy = MeshBuilder.CreateBox('canopy', { width: 4.2, height: 0.22, depth: 2.4 }, scene)
  canopy.position.set(0, 2.8, 0.3)
  canopy.material = roof
  canopy.parent = stall
  for (const [x, z] of [
    [-1.9, -0.6],
    [1.9, -0.6],
    [-1.9, 1.3],
    [1.9, 1.3],
  ]) {
    const post = MeshBuilder.CreateCylinder('post', { height: 2.8, diameter: 0.2 }, scene)
    post.position.set(x, 1.4, z)
    post.material = mats.wall
    post.parent = stall
  }
  colliders.addBox(sx, sz + 0.3, 4.2, 2, sy, sy + 1.1)
  casters.push(counter, canopy)
}

function createLighthouse(scene, urls, mats, colliders, casters) {
  const { x, z } = PLACES.lighthouse
  const base = heightAt(x, z) - 0.5
  const root = new TransformNode('lighthouse', scene)
  root.position.set(x, base, z)
  const stripe = toonMaterial(scene, 'lighthouse-stripe', '#F2B3C6')
  const tower = MeshBuilder.CreateCylinder('tower', { height: 15, diameterTop: 2.8, diameterBottom: 4.2, tessellation: 24 }, scene)
  tower.position.y = 7.5
  tower.material = mats.wall
  tower.parent = root
  for (const y of [4, 9.5]) {
    const band = MeshBuilder.CreateCylinder('band', { height: 1.2, diameter: 4.2 - y * 0.093, tessellation: 24 }, scene)
    band.position.y = y
    band.material = stripe
    band.parent = root
  }
  const deck = MeshBuilder.CreateCylinder('deck', { height: 0.4, diameter: 4.4, tessellation: 24 }, scene)
  deck.position.y = 15.2
  deck.material = mats.wall
  deck.parent = root
  const lamp = MeshBuilder.CreateCylinder('lamp', { height: 1.6, diameter: 2, tessellation: 16 }, scene)
  lamp.position.y = 16.2
  lamp.material = mats.lamp
  lamp.parent = root
  const cap = MeshBuilder.CreateCylinder('cap', { height: 1.8, diameterTop: 0, diameterBottom: 3, tessellation: 24 }, scene)
  cap.position.y = 17.9
  cap.material = stripe
  cap.parent = root
  colliders.addCircle(x, z, 2.1, base, base + 15.4, true)
  casters.push(tower, cap)
}

// 瞭望台：石头塔身、木平台、四根柱子撑一顶小尖帽，侧面能爬
function createTowers(scene, urls, mats, colliders, casters) {
  const stone = toonMaterial(scene, 'tower-stone', '#FFFFFF', tiled(urls.rock, scene, 2))
  const wood = toonMaterial(scene, 'tower-wood', '#D8B48C')
  const cap = toonMaterial(scene, 'tower-cap', '#C3B4EC')
  const flag = toonMaterial(scene, 'tower-flag', '#F2B3C6')
  flag.backFaceCulling = false
  for (const [i, t] of TOWERS.entries()) {
    const base = heightAt(t.x, t.z) - 1
    const root = new TransformNode('tower' + i, scene)
    root.position.set(t.x, base, t.z)
    const core = MeshBuilder.CreateBox('tower-core' + i, { width: 3.4, height: t.h + 1, depth: 3.4 }, scene)
    core.position.y = (t.h + 1) / 2
    core.material = stone
    core.parent = root
    const deck = MeshBuilder.CreateBox('tower-deck' + i, { width: 5.2, height: 0.5, depth: 5.2 }, scene)
    deck.position.y = t.h + 0.75
    deck.material = wood
    deck.parent = root
    for (const [px, pz] of [
      [-2.3, -2.3],
      [2.3, -2.3],
      [-2.3, 2.3],
      [2.3, 2.3],
    ]) {
      const post = MeshBuilder.CreateCylinder('tower-post', { height: 3, diameter: 0.22 }, scene)
      post.position.set(px, t.h + 2.5, pz)
      post.material = wood
      post.parent = root
    }
    const hat = MeshBuilder.CreateCylinder('tower-hat' + i, { height: 2.4, diameterTop: 0, diameterBottom: 7.2, tessellation: 4 }, scene)
    hat.rotation.y = Math.PI / 4
    hat.position.y = t.h + 5.1
    hat.material = cap
    hat.parent = root
    const pole = MeshBuilder.CreateCylinder('tower-pole', { height: 3, diameter: 0.1 }, scene)
    pole.position.y = t.h + 7.4
    pole.material = wood
    pole.parent = root
    const banner = MeshBuilder.CreatePlane('tower-flag' + i, { width: 1.6, height: 0.9 }, scene)
    banner.position.set(0.8, t.h + 8.4, 0)
    banner.material = flag
    banner.parent = root
    colliders.addBox(t.x, t.z, 5.2, 5.2, base + t.h + 0.5, base + t.h + 1, true)
    colliders.addBox(t.x, t.z, 3.4, 3.4, base, base + t.h + 1, true)
    casters.push(core, deck, hat)
  }
}

function createBoulders(scene, mats, colliders, casters) {
  const rand = random(53)
  const mesh = boulderMesh(scene, 'boulder', 61)
  mesh.material = mats.stone
  mesh.receiveShadows = true
  const list = []
  for (let tries = 0; list.length < 70 && tries < 4000; tries++) {
    const angle = rand() * Math.PI * 2
    const r = Math.sqrt(rand()) * 330
    const x = Math.cos(angle) * r
    const z = Math.sin(angle) * r
    const h = heightAt(x, z)
    if (h < 3 || pathDistance(x, z) < 6 || slopeAt(x, z) > 0.8) continue
    if (Math.hypot(x - PLACES.village.x, z - PLACES.village.z) < PLACES.village.r + 8) continue
    if (list.some((b) => Math.hypot(b.x - x, b.z - z) < 14)) continue
    const size = 1.6 + rand() * rand() * 4.5
    list.push({ x, z, size, y: h - size * 0.25 })
  }
  const buffer = new Float32Array(list.length * 16)
  list.forEach((b, i) => {
    Matrix.Compose(new Vector3(b.size, b.size, b.size), Quaternion.RotationAxis(Vector3.Up(), rand() * Math.PI * 2), new Vector3(b.x, b.y, b.z)).copyToArray(buffer, i * 16)
    colliders.addCircle(b.x, b.z, b.size * 0.92, b.y - b.size, b.y + b.size * 0.78, true)
  })
  mesh.thinInstanceSetBuffer('matrix', buffer, 16, true)
  casters.push(mesh)
  return list
}

function createGiantTree(scene, urls, mats, colliders, casters) {
  const { x, z, r, height } = GIANT_TREE
  const base = heightAt(x, z) - 1
  const root = new TransformNode('giant-tree', scene)
  root.position.set(x, base, z)
  const bark = toonMaterial(scene, 'giant-bark', '#FFFFFF', tiled(urls.bark, scene, 3))
  const trunk = MeshBuilder.CreateCylinder('giant-trunk', { height, diameterTop: r * 1.4, diameterBottom: r * 2.4, tessellation: 18 }, scene)
  trunk.position.y = height / 2
  trunk.material = bark
  trunk.parent = root
  const leaf = toonMaterial(scene, 'giant-leaf', '#FFFFFF', tiled(urls.leaf, scene, 5))
  const parts = [
    [0, height + 4, 0, 26],
    [9, height - 1, 3, 17],
    [-8, height, -4, 18],
    [2, height + 1, -9, 16],
    [-3, height + 2, 9, 16],
    [0, height + 11, 0, 15],
  ].map(([px, py, pz, d], i) => {
    const s = MeshBuilder.CreateSphere('giant-crown' + i, { diameter: d, segments: 14 }, scene)
    s.position.set(px, py, pz)
    return s
  })
  const crown = Mesh.MergeMeshes(parts, true)
  crown.name = 'giant-crown'
  crown.material = leaf
  crown.parent = root
  colliders.addCircle(x, z, r * 1.05, base, base + height - 2, true)
  // 树冠底下能站人的大枝杈
  colliders.addCircle(x, z, 11, base + height - 2, base + height - 1, false)
  casters.push(trunk, crown)
}

function createSkyIsland(scene, urls, mats, island, colliders, casters) {
  const grass = toonMaterial(scene, 'sky-grass', '#FFFFFF', tiled(urls.grass, scene, 4))
  const top = MeshBuilder.CreateCylinder('sky-top', { height: 3, diameterTop: island.r * 2, diameterBottom: island.r * 1.85, tessellation: 40 }, scene)
  top.position.set(island.x, island.top - 1.5, island.z)
  top.material = grass
  const under = MeshBuilder.CreateCylinder('sky-under', { height: 34, diameterTop: island.r * 1.85, diameterBottom: 2, tessellation: 24, subdivisions: 6 }, scene)
  const positions = under.getVerticesData('position')
  const jitter = random(71)
  for (let i = 0; i < positions.length; i += 3) {
    const k = 0.85 + jitter() * 0.3
    positions[i] *= k
    positions[i + 2] *= k
  }
  under.setVerticesData('position', positions)
  const normals = []
  VertexData.ComputeNormals(positions, under.getIndices(), normals)
  under.setVerticesData('normal', normals)
  under.position.set(island.x, island.top - 3 - 17, island.z)
  under.material = mats.stone
  colliders.addCircle(island.x, island.z, island.r, island.top - 30, island.top, true)
  casters.push(top, under)
  // 岛上沿边种几棵小树和几丛灌木，中间留给神殿
  const leaf = toonMaterial(scene, 'sky-leaf', '#FFFFFF', tiled(urls.leaf, scene, 2))
  const bark = toonMaterial(scene, 'sky-bark', '#FFFFFF', tiled(urls.bark, scene, 1))
  const petals = ['#F7B6C8', '#FFF6EC', '#C9B8F0', '#F8DE7E'].map((hex) => toonMaterial(scene, 'sky-petal' + hex, hex))
  const rand = random(83)
  for (let i = 0; i < 14; i++) {
    const angle = (i / 14) * Math.PI * 2 + rand() * 0.3
    const r = island.r * (0.72 + rand() * 0.16)
    const x = island.x + Math.cos(angle) * r
    const z = island.z + Math.sin(angle) * r
    if (i % 4 === 0) {
      const trunk = MeshBuilder.CreateCylinder('sky-trunk' + i, { height: 2.6, diameterTop: 0.35, diameterBottom: 0.55, tessellation: 8 }, scene)
      trunk.position.set(x, island.top + 1.3, z)
      trunk.material = bark
      const crown = MeshBuilder.CreateSphere('sky-crown' + i, { diameter: 3, segments: 10 }, scene)
      crown.position.set(x, island.top + 3.4, z)
      crown.material = leaf
      colliders.addCircle(x, z, 0.35, island.top, island.top + 2.6)
      casters.push(trunk, crown)
    } else {
      const bush = MeshBuilder.CreateSphere('sky-bush' + i, { diameter: 1.4 + rand(), segments: 8 }, scene)
      bush.scaling.y = 0.7
      bush.position.set(x, island.top + 0.4, z)
      bush.material = leaf
      // 灌木脚下点缀几朵小花
      for (let k = 0; k < 3; k++) {
        const flower = MeshBuilder.CreateSphere('sky-flower', { diameter: 0.28, segments: 6 }, scene)
        flower.scaling.y = 0.5
        flower.position.set(x + (rand() - 0.5) * 2.4, island.top + 0.12, z + (rand() - 0.5) * 2.4)
        flower.material = petals[Math.floor(rand() * petals.length)]
      }
      casters.push(bush)
    }
  }
}

// 神殿：圆台、六根柱子、粉色圆顶；点亮后圆顶发光，三枚星星在顶上转
function createShrine(scene, urls, mats, at, colliders, casters) {
  const root = new TransformNode('shrine', scene)
  root.position.set(at.x, at.y, at.z)
  const roofMaterial = toonMaterial(scene, 'shrine-roof', '#FFFFFF', tiled(urls.roof, scene, 3))
  const base = MeshBuilder.CreateCylinder('shrine-base', { height: 0.6, diameter: 10, tessellation: 32 }, scene)
  base.position.y = 0.3
  base.material = mats.wall
  base.parent = root
  for (let i = 0; i < 6; i++) {
    const angle = (i / 6) * Math.PI * 2
    const pillar = MeshBuilder.CreateCylinder('pillar' + i, { height: 4.4, diameter: 0.7, tessellation: 12 }, scene)
    pillar.position.set(Math.cos(angle) * 4, 2.8, Math.sin(angle) * 4)
    pillar.material = mats.wall
    pillar.parent = root
    colliders.addCircle(at.x + Math.cos(angle) * 4, at.z + Math.sin(angle) * 4, 0.45, at.y, at.y + 5)
    casters.push(pillar)
  }
  const dome = MeshBuilder.CreateSphere('dome', { diameter: 9.6, slice: 0.5, segments: 24 }, scene)
  dome.position.y = 5
  dome.scaling.y = 0.6
  dome.material = roofMaterial
  dome.parent = root
  const pedestal = MeshBuilder.CreateCylinder('pedestal', { height: 1.2, diameterTop: 1, diameterBottom: 1.5, tessellation: 16 }, scene)
  pedestal.position.y = 1.2
  pedestal.material = mats.wall
  pedestal.parent = root
  colliders.addCircle(at.x, at.z, 0.9, at.y, at.y + 1.8)
  casters.push(dome)

  const starMaterial = toonMaterial(scene, 'shrine-star', '#FFFFFF', new Texture(urls.star, scene))
  starMaterial.diffuseTexture.hasAlpha = true
  starMaterial.emissiveColor = new Color3(1, 0.95, 0.7)
  starMaterial.disableLighting = true
  starMaterial.backFaceCulling = false
  const stars = [0, 1, 2].map((i) => {
    const star = MeshBuilder.CreatePlane('shrine-star' + i, { size: 1.2 }, scene)
    star.billboardMode = Mesh.BILLBOARDMODE_ALL
    star.material = starMaterial
    star.parent = root
    star.setEnabled(false)
    return star
  })
  let lit = false
  return {
    light() {
      lit = true
      roofMaterial.emissiveColor = new Color3(0.35, 0.28, 0.12)
      stars.forEach((s) => s.setEnabled(true))
    },
    update(time) {
      if (!lit) return
      stars.forEach((s, i) => {
        const a = time * 0.8 + (i / 3) * Math.PI * 2
        s.position.set(Math.cos(a) * 1.3, 2.9 + Math.sin(time * 2 + i) * 0.15, Math.sin(a) * 1.3)
      })
    },
  }
}

// 上升气流：一柱往上飘的光点，告诉玩家这里能乘风上去
function createUpdrafts(scene, dot) {
  for (const u of UPDRAFTS) {
    const ps = new ParticleSystem('updraft', 260, scene)
    ps.particleTexture = dot
    const base = heightAt(u.x, u.z)
    ps.emitter = new Vector3(u.x, base, u.z)
    ps.minEmitBox = new Vector3(-u.r, 0, -u.r)
    ps.maxEmitBox = new Vector3(u.r, 2, u.r)
    ps.direction1 = new Vector3(-0.3, 1, -0.3)
    ps.direction2 = new Vector3(0.3, 1, 0.3)
    ps.minEmitPower = 10
    ps.maxEmitPower = 16
    ps.minLifeTime = (u.top - base) / 16
    ps.maxLifeTime = (u.top - base) / 11
    ps.minSize = 0.3
    ps.maxSize = 0.8
    ps.color1 = new Color4(1, 1, 1, 0.7)
    ps.color2 = new Color4(0.85, 0.95, 1, 0.5)
    ps.colorDead = new Color4(1, 1, 1, 0)
    ps.emitRate = 70
    ps.blendMode = ParticleSystem.BLENDMODE_ADD
    ps.start()
  }
}

export function createStructures(scene, urls, world, colliders, casters, dot) {
  const mats = {
    wall: toonMaterial(scene, 'wall', '#FFFFFF', tiled(urls.wall, scene, 2)),
    stone: toonMaterial(scene, 'stone', '#FFFFFF', tiled(urls.rock, scene, 1.5)),
    glass: toonMaterial(scene, 'glass', '#BFE3F2'),
    lamp: toonMaterial(scene, 'lamp', '#FFF3B8'),
  }
  mats.lamp.emissiveColor = Color3.FromHexString('#FFE58A')
  createVillage(scene, urls, mats, colliders, casters)
  createLighthouse(scene, urls, mats, colliders, casters)
  createTowers(scene, urls, mats, colliders, casters)
  const boulders = createBoulders(scene, mats, colliders, casters)
  createGiantTree(scene, urls, mats, colliders, casters)
  for (const island of SKY_ISLANDS) createSkyIsland(scene, urls, mats, island, colliders, casters)
  const island = SKY_ISLANDS[0]
  const shrine = createShrine(scene, urls, mats, { x: world.shrine.x, y: island.top, z: world.shrine.z }, colliders, casters)
  createUpdrafts(scene, dot)
  return {
    shrine,
    boulders,
    // 夜里窗户和灯塔亮起来
    setNight(night) {
      mats.glass.emissiveColor = Color3.Lerp(new Color3(0.15, 0.2, 0.25), new Color3(1, 0.82, 0.45), night)
      mats.lamp.emissiveColor = Color3.Lerp(Color3.FromHexString('#FFE58A').scale(0.6), Color3.FromHexString('#FFE58A'), night)
    },
  }
}
