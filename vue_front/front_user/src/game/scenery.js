// 萌岛的布景：天空、海、地形、树和花草、小村、灯塔、山顶神殿和天上的云
import { MeshBuilder } from '@babylonjs/core/Meshes/meshBuilder'
import { Mesh } from '@babylonjs/core/Meshes/mesh'
import { VertexData } from '@babylonjs/core/Meshes/mesh.vertexData'
import { VertexBuffer } from '@babylonjs/core/Buffers/buffer'
import { TransformNode } from '@babylonjs/core/Meshes/transformNode'
import { StandardMaterial } from '@babylonjs/core/Materials/standardMaterial'
import { Texture } from '@babylonjs/core/Materials/Textures/texture'
import { RawTexture } from '@babylonjs/core/Materials/Textures/rawTexture'
import { Color3 } from '@babylonjs/core/Maths/math.color'
import { Vector4 } from '@babylonjs/core/Maths/math.vector'
import { MixMaterial } from '@babylonjs/materials/mix/mixMaterial'
import { createFlora } from './flora'
import {
  HOUSES,
  ISLAND,
  PLACES,
  SAND_LINE,
  SHOP,
  heightAt,
  pathDistance,
  random,
  slopeAt,
  smoothstep,
} from './terrain'

// 数量和精度集中在这里，显卡吃力时先调这几个
const Quality = Object.freeze({
  TERRAIN_SUBDIVISIONS: 200,
  MIX_SIZE: 512,
  CLOUDS: 12,
})

function flat(scene, name, hex, texture) {
  const m = new StandardMaterial(name, scene)
  m.diffuseColor = Color3.FromHexString(hex)
  m.specularColor = new Color3(0.05, 0.05, 0.05)
  if (texture) m.diffuseTexture = texture
  return m
}

function tiled(url, scene, scale) {
  const t = new Texture(url, scene)
  t.uScale = scale
  t.vScale = scale
  return t
}

// 天空：一个内翻的大球贴全景图，不受光照和雾影响
function createSky(scene, urls) {
  const sky = MeshBuilder.CreateSphere('sky', { diameter: 900, segments: 24, sideOrientation: Mesh.BACKSIDE }, scene)
  const m = new StandardMaterial('sky', scene)
  // 自发光颜色会加在贴图上，保持黑色，天空只显示贴图本身
  m.emissiveTexture = new Texture(urls.sky, scene)
  m.emissiveTexture.uScale = -1
  m.emissiveTexture.vScale = -1
  m.emissiveColor = Color3.Black()
  m.diffuseColor = Color3.Black()
  m.specularColor = Color3.Black()
  m.disableLighting = true
  m.backFaceCulling = false
  sky.material = m
  sky.infiniteDistance = true
  // 雾只给地面和近景，天空和云不吃雾
  sky.applyFog = false
  sky.isPickable = false
  return m
}

function createSea(scene, urls) {
  const sea = MeshBuilder.CreateDisc('sea', { radius: 460, tessellation: 64 }, scene)
  sea.rotation.x = Math.PI / 2
  sea.position.y = 0
  const texture = tiled(urls.water, scene, 90)
  const m = flat(scene, 'sea', '#BFE6F2', texture)
  m.alpha = 0.86
  m.specularColor = new Color3(0.25, 0.25, 0.25)
  m.specularPower = 48
  sea.material = m
  sea.isPickable = false
  return texture
}

// 地形网格按高度函数抬起；草、沙、岩石、小路四种贴图按高度、坡度和到小路的距离混合
function createGround(scene, urls) {
  const size = ISLAND.size
  const ground = MeshBuilder.CreateGround(
    'ground',
    { width: size, height: size, subdivisions: Quality.TERRAIN_SUBDIVISIONS, updatable: true },
    scene,
  )
  const positions = ground.getVerticesData(VertexBuffer.PositionKind)
  for (let i = 0; i < positions.length; i += 3) positions[i + 1] = heightAt(positions[i], positions[i + 2])
  const normals = []
  VertexData.ComputeNormals(positions, ground.getIndices(), normals)
  ground.updateVerticesData(VertexBuffer.PositionKind, positions)
  ground.updateVerticesData(VertexBuffer.NormalKind, normals)
  ground.refreshBoundingInfo()

  // 混合图：r 恒为 1 打底是草，g 是沙，b 是岩石，a 越小越像小路
  const n = Quality.MIX_SIZE
  const data = new Uint8Array(n * n * 4)
  for (let py = 0; py < n; py++)
    for (let px = 0; px < n; px++) {
      const x = ((px + 0.5) / n) * size - size / 2
      const z = ((py + 0.5) / n) * size - size / 2
      const h = heightAt(x, z)
      // 沙滩只在海边，岛中间的低洼处还是草地
      const sand = smoothstep(SAND_LINE + 0.4, SAND_LINE - 0.3, h) * smoothstep(72, 92, Math.hypot(x, z))
      const rock = Math.max(smoothstep(0.55, 0.9, slopeAt(x, z)), smoothstep(23, 27, h) * 0.35)
      const path = h > SAND_LINE - 0.2 ? smoothstep(2.6, 1.4, pathDistance(x, z)) : 0
      const i = (py * n + px) * 4
      data[i] = 255
      data[i + 1] = Math.round(sand * 255)
      data[i + 2] = Math.round(rock * 255)
      data[i + 3] = Math.round((1 - path * 0.92) * 255)
    }
  const mix = RawTexture.CreateRGBATexture(data, n, n, scene, true, false, Texture.BILINEAR_SAMPLINGMODE)
  mix.wrapU = Texture.CLAMP_ADDRESSMODE
  mix.wrapV = Texture.CLAMP_ADDRESSMODE

  const m = new MixMaterial('ground', scene)
  m.mixTexture1 = mix
  m.diffuseTexture1 = tiled(urls.grass, scene, 40)
  m.diffuseTexture2 = tiled(urls.sand, scene, 40)
  m.diffuseTexture3 = tiled(urls.rock, scene, 34)
  m.diffuseTexture4 = tiled(urls.path, scene, 46)
  m.specularColor = new Color3(0.02, 0.02, 0.02)
  ground.material = m
  ground.receiveShadows = true
  ground.isPickable = false
  return ground
}

// 双坡屋顶：屋脊沿 x 方向，底面宽 w、深 d、高 h，两面坡和两个山墙都是平面
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
  for (const face of faces) {
    const start = positions.length / 3
    face.forEach(([px, py, pz]) => {
      positions.push(px, py, pz)
      uvs.push(face.length === 4 ? (px + x) / 2 : (pz + z) / 2, face.length === 4 ? py / 1.2 : py / 2)
    })
    indices.push(start, start + 2, start + 1)
    if (face.length === 4) indices.push(start, start + 3, start + 2)
  }
  const normals = []
  VertexData.ComputeNormals(positions, indices, normals)
  const data = new VertexData()
  Object.assign(data, { positions, indices, normals, uvs })
  const mesh = new Mesh(name, scene)
  data.applyToMesh(mesh)
  return mesh
}

// 小村：四间房子、商店摊位
function createVillage(scene, urls, colliders, casters) {
  const wall = flat(scene, 'wall', '#FFFFFF', tiled(urls.wall, scene, 2))
  wall.emissiveColor = new Color3(0.16, 0.15, 0.14)
  const roofTexture = tiled(urls.roof, scene, 2)
  const roof = flat(scene, 'roof', '#FFFFFF', roofTexture)
  roof.backFaceCulling = false
  const door = flat(scene, 'door', '#C99C79')
  const glass = flat(scene, 'glass', '#BFE3F2')
  glass.emissiveColor = new Color3(0.2, 0.25, 0.3)
  for (const [i, h] of HOUSES.entries()) {
    const base = heightAt(h.x, h.z) - 0.6
    const house = new TransformNode('house' + i, scene)
    house.position.set(h.x, base, h.z)
    // 门朝向村子中心
    const facing = Math.abs(h.z - PLACES.village.z) > Math.abs(h.x - PLACES.village.x) ? (h.z > PLACES.village.z ? Math.PI : 0) : h.x > PLACES.village.x ? -Math.PI / 2 : Math.PI / 2
    const body = MeshBuilder.CreateBox('wall' + i, { width: h.w, height: 3.6, depth: h.d }, scene)
    body.position.y = 1.8
    body.material = wall
    body.parent = house
    const top = gableRoof(scene, 'roof' + i, h.w + 0.9, h.d + 0.9, 2.4)
    top.position.y = 3.6
    top.material = roof
    top.parent = house
    const front = new TransformNode('front' + i, scene)
    front.parent = house
    front.rotation.y = facing
    const depth = Math.abs(Math.sin(facing)) > 0.5 ? h.w : h.d
    const doorMesh = MeshBuilder.CreateBox('door' + i, { width: 1.2, height: 2, depth: 0.12 }, scene)
    doorMesh.position.set(0, 1, -depth / 2 - 0.04)
    doorMesh.material = door
    doorMesh.parent = front
    for (const side of [-1, 1]) {
      const window = MeshBuilder.CreateBox('window' + i + side, { width: 0.9, height: 0.8, depth: 0.1 }, scene)
      window.position.set(side * 1.9, 2.1, -depth / 2 - 0.04)
      window.material = glass
      window.parent = front
    }
    colliders.addBox(h.x, h.z, h.w, h.d)
    casters.push(body, top)
  }
  // 摊位：在团子身后，四根柱子撑一块粉色顶棚
  const stall = new TransformNode('stall', scene)
  stall.position.set(SHOP.x, heightAt(SHOP.x, SHOP.z + 2), SHOP.z + 2)
  const counter = MeshBuilder.CreateBox('counter', { width: 3.4, height: 1, depth: 1 }, scene)
  counter.position.y = 0.5
  counter.material = door
  counter.parent = stall
  const canopy = MeshBuilder.CreateBox('canopy', { width: 4, height: 0.2, depth: 2.2 }, scene)
  canopy.position.set(0, 2.7, 0.3)
  canopy.material = roof
  canopy.parent = stall
  for (const [x, z] of [
    [-1.8, -0.6],
    [1.8, -0.6],
    [-1.8, 1.2],
    [1.8, 1.2],
  ]) {
    const post = MeshBuilder.CreateCylinder('post', { height: 2.7, diameter: 0.18 }, scene)
    post.position.set(x, 1.35, z)
    post.material = wall
    post.parent = stall
  }
  colliders.addBox(SHOP.x, SHOP.z + 2.3, 4, 1.8)
  casters.push(counter, canopy)
}

function createLighthouse(scene, urls, colliders, casters) {
  const { x, z } = PLACES.lighthouse
  const root = new TransformNode('lighthouse', scene)
  root.position.set(x, heightAt(x, z) - 0.4, z)
  const wall = flat(scene, 'lighthouse-wall', '#FFFFFF', tiled(urls.wall, scene, 3))
  const stripe = flat(scene, 'lighthouse-stripe', '#F2B3C6')
  const tower = MeshBuilder.CreateCylinder('tower', { height: 10, diameterTop: 2.4, diameterBottom: 3.4, tessellation: 24 }, scene)
  tower.position.y = 5
  tower.material = wall
  tower.parent = root
  for (const y of [3, 6.5]) {
    const band = MeshBuilder.CreateCylinder('band', { height: 0.9, diameterTop: 3.4 - (y + 0.45) * 0.1, diameterBottom: 3.4 - (y - 0.45) * 0.1 + 0.05, tessellation: 24 }, scene)
    band.position.y = y
    band.material = stripe
    band.parent = root
  }
  const lamp = MeshBuilder.CreateCylinder('lamp', { height: 1.4, diameter: 1.8, tessellation: 16 }, scene)
  lamp.position.y = 10.7
  const glow = flat(scene, 'lamp', '#FFF3B8')
  glow.emissiveColor = Color3.FromHexString('#FFE58A')
  lamp.material = glow
  lamp.parent = root
  const cap = MeshBuilder.CreateCylinder('cap', { height: 1.6, diameterTop: 0, diameterBottom: 2.6, tessellation: 24 }, scene)
  cap.position.y = 12.2
  cap.material = stripe
  cap.parent = root
  colliders.addCircle(x, z, 1.8)
  casters.push(tower, cap)
}

// 神殿：圆台、六根柱子、粉色圆顶；点亮后圆顶发光，三枚星星在顶上转
function createShrine(scene, urls, shrine, colliders, casters) {
  const root = new TransformNode('shrine', scene)
  root.position.set(shrine.x, heightAt(shrine.x, shrine.z) - 0.1, shrine.z)
  const stone = flat(scene, 'shrine-stone', '#FFFFFF', tiled(urls.wall, scene, 3))
  const roofMaterial = flat(scene, 'shrine-roof', '#FFFFFF', tiled(urls.roof, scene, 3))
  const base = MeshBuilder.CreateCylinder('shrine-base', { height: 0.6, diameter: 9, tessellation: 32 }, scene)
  base.position.y = 0.3
  base.material = stone
  base.parent = root
  for (let i = 0; i < 6; i++) {
    const angle = (i / 6) * Math.PI * 2
    const pillar = MeshBuilder.CreateCylinder('pillar' + i, { height: 4, diameter: 0.6, tessellation: 12 }, scene)
    pillar.position.set(Math.cos(angle) * 3.6, 2.6, Math.sin(angle) * 3.6)
    pillar.material = stone
    pillar.parent = root
    colliders.addCircle(shrine.x + Math.cos(angle) * 3.6, shrine.z + Math.sin(angle) * 3.6, 0.4)
    casters.push(pillar)
  }
  const dome = MeshBuilder.CreateSphere('dome', { diameter: 8.6, slice: 0.5, segments: 24 }, scene)
  dome.position.y = 4.6
  dome.scaling.y = 0.6
  dome.material = roofMaterial
  dome.parent = root
  const pedestal = MeshBuilder.CreateCylinder('pedestal', { height: 1.1, diameterTop: 1, diameterBottom: 1.4, tessellation: 16 }, scene)
  pedestal.position.y = 1.15
  pedestal.material = stone
  pedestal.parent = root
  colliders.addCircle(shrine.x, shrine.z, 0.9)
  casters.push(dome)

  const starMaterial = new StandardMaterial('shrine-star', scene)
  starMaterial.diffuseTexture = new Texture(urls.star, scene)
  starMaterial.diffuseTexture.hasAlpha = true
  starMaterial.emissiveColor = new Color3(1, 0.95, 0.7)
  starMaterial.disableLighting = true
  starMaterial.backFaceCulling = false
  const stars = [0, 1, 2].map((i) => {
    const star = MeshBuilder.CreatePlane('shrine-star' + i, { size: 1.1 }, scene)
    star.billboardMode = Mesh.BILLBOARDMODE_ALL
    star.material = starMaterial
    star.parent = root
    star.setEnabled(false)
    return star
  })
  let lit = false
  function light() {
    lit = true
    roofMaterial.emissiveColor = new Color3(0.35, 0.28, 0.12)
    stone.emissiveColor = new Color3(0.18, 0.16, 0.1)
    stars.forEach((s) => s.setEnabled(true))
  }
  function update(time) {
    if (!lit) return
    stars.forEach((s, i) => {
      const a = time * 0.8 + (i / 3) * Math.PI * 2
      s.position.set(Math.cos(a) * 1.2, 2.6 + Math.sin(time * 2 + i) * 0.15, Math.sin(a) * 1.2)
    })
  }
  return { light, update }
}

function createClouds(scene, urls) {
  const texture = new Texture(urls.clouds, scene)
  texture.hasAlpha = true
  const m = new StandardMaterial('cloud', scene)
  m.diffuseTexture = texture
  m.useAlphaFromDiffuseTexture = true
  m.disableLighting = true
  m.emissiveColor = new Color3(1, 1, 1)
  m.backFaceCulling = false
  const rand = random(23)
  const clouds = []
  for (let i = 0; i < Quality.CLOUDS; i++) {
    const cell = i % 3
    const uv = new Vector4(cell / 3, 0, (cell + 1) / 3, 1)
    const width = 30 + rand() * 26
    const cloud = MeshBuilder.CreatePlane('cloud' + i, { width, height: width * (1.5 / 1), frontUVs: uv, backUVs: uv, sideOrientation: Mesh.DOUBLESIDE }, scene)
    cloud.scaling.y = 0.42
    cloud.billboardMode = Mesh.BILLBOARDMODE_Y
    cloud.material = m
    cloud.isPickable = false
    cloud.applyFog = false
    clouds.push({ mesh: cloud, angle: rand() * Math.PI * 2, radius: 150 + rand() * 120, y: 45 + rand() * 35, speed: 0.004 + rand() * 0.006 })
  }
  return (time) => {
    for (const c of clouds) {
      const a = c.angle + time * c.speed
      c.mesh.position.set(Math.cos(a) * c.radius, c.y, Math.sin(a) * c.radius)
    }
  }
}

// 整个布景；blocked 是点位和地标附近不种东西的圆
export function createScenery(scene, urls, world, colliders) {
  const casters = []
  const blocked = [
    ...world.spots.map((s) => ({ x: s.x, z: s.z, r: 2.6 })),
    { x: world.shrine.x, z: world.shrine.z, r: 9 },
    { x: PLACES.lighthouse.x, z: PLACES.lighthouse.z, r: 5 },
    { x: PLACES.village.x, z: PLACES.village.z, r: PLACES.village.r - 4 },
    { x: PLACES.spawn.x, z: PLACES.spawn.z, r: 6 },
  ]
  const skyMaterial = createSky(scene, urls)
  const water = createSea(scene, urls)
  const ground = createGround(scene, urls)
  const trees = createFlora(scene, urls, blocked, colliders, casters)
  createVillage(scene, urls, colliders, casters)
  createLighthouse(scene, urls, colliders, casters)
  const shrine = createShrine(scene, urls, world.shrine, colliders, casters)
  const clouds = createClouds(scene, urls)
  function update(time) {
    water.uOffset = (time * 0.004) % 1
    water.vOffset = (time * 0.0025) % 1
    clouds(time)
    shrine.update(time)
  }
  return { ground, casters, skyMaterial, shrine, trees, update }
}
