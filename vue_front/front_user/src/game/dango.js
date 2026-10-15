// 团子：球身子、小脚、脸贴图，玩家和商店老板共用；帽子和围巾按装备换
import { MeshBuilder } from '@babylonjs/core/Meshes/meshBuilder'
import { TransformNode } from '@babylonjs/core/Meshes/transformNode'
import { StandardMaterial } from '@babylonjs/core/Materials/standardMaterial'
import { Texture } from '@babylonjs/core/Materials/Textures/texture'
import { Color3 } from '@babylonjs/core/Maths/math.color'
import { OUTFIT_COLORS } from '@/constants/game'

export const DANGO_RADIUS = 0.6

// 脸贴图三格：普通、开心、惊讶
export const Face = Object.freeze({ NORMAL: 0, HAPPY: 1, SURPRISED: 2 })

// 和脸贴图底色一致，脸和身子接缝看不出来
const SKIN = Color3.FromHexString('#FFF8EC')

function material(scene, name, hex) {
  const m = new StandardMaterial(name, scene)
  m.diffuseColor = Color3.FromHexString(hex)
  m.specularColor = new Color3(0.08, 0.08, 0.08)
  return m
}

export function createDango(scene, faceUrl, name) {
  const root = new TransformNode(name, scene)
  // 身子单独一层，走路和跳的时候压扁拉长，脚不跟着变形
  const body = new TransformNode(name + '-body', scene)
  body.parent = root
  body.position.y = DANGO_RADIUS

  const skin = new StandardMaterial(name + '-skin', scene)
  skin.diffuseColor = SKIN
  skin.specularColor = new Color3(0.1, 0.1, 0.1)
  const ball = MeshBuilder.CreateSphere(name + '-ball', { diameter: DANGO_RADIUS * 2, segments: 24 }, scene)
  ball.material = skin
  ball.parent = body
  ball.scaling.y = 0.92

  // 脸是一片稍大一点的球面，只占正面三分之一圈
  const faceTexture = new Texture(faceUrl, scene)
  faceTexture.wrapU = Texture.CLAMP_ADDRESSMODE
  faceTexture.wrapV = Texture.CLAMP_ADDRESSMODE
  faceTexture.uScale = 1 / 3
  const faceMaterial = new StandardMaterial(name + '-face', scene)
  faceMaterial.diffuseTexture = faceTexture
  faceMaterial.specularColor = new Color3(0.1, 0.1, 0.1)
  const face = MeshBuilder.CreateSphere(
    name + '-face',
    { diameter: DANGO_RADIUS * 2 + 0.012, segments: 24, arc: 1 / 3 },
    scene,
  )
  face.material = faceMaterial
  face.parent = ball
  // 贴图在球面上是倒着的，先绕前后轴转半圈摆正；这时球面片中心在 -150° 方向，再转回正对 +z
  face.rotation.z = Math.PI
  face.rotation.y = Math.PI / 2 + Math.PI / 3

  const feetMaterial = material(scene, name + '-feet', '#F6E3CF')
  const feet = [-0.25, 0.25].map((x, i) => {
    const foot = MeshBuilder.CreateSphere(name + '-foot' + i, { diameter: 0.34, segments: 10 }, scene)
    foot.scaling.set(1, 0.55, 1.25)
    foot.position.set(x, 0.08, 0.05)
    foot.material = feetMaterial
    foot.parent = root
    return foot
  })

  const parts = { hat: null, scarf: null }
  const meshes = () => root.getChildMeshes()

  function setFace(index) {
    faceTexture.uOffset = index / 3
  }

  // 帽子：叶子、蝴蝶结、小皇冠；传空就摘掉
  function setHat(id) {
    parts.hat?.dispose(false, true)
    parts.hat = null
    if (!id) return
    const hat = new TransformNode(name + '-hat', scene)
    hat.parent = ball
    hat.position.y = DANGO_RADIUS * 0.86
    if (id === 'hat-leaf') {
      const leaf = material(scene, name + '-leaf', '#7CC79A')
      const stem = MeshBuilder.CreateCylinder(name + '-stem', { height: 0.22, diameter: 0.05 }, scene)
      stem.material = leaf
      stem.parent = hat
      stem.position.y = 0.08
      for (const side of [-1, 1]) {
        const blade = MeshBuilder.CreateSphere(name + '-blade' + side, { diameter: 0.3, segments: 10 }, scene)
        blade.scaling.set(1.2, 0.25, 0.6)
        blade.position.set(side * 0.15, 0.2, 0)
        blade.rotation.z = side * -0.5
        blade.material = leaf
        blade.parent = hat
      }
    } else if (id === 'hat-bow') {
      const ribbon = material(scene, name + '-ribbon', '#F2A7BE')
      for (const side of [-1, 1]) {
        const loop = MeshBuilder.CreateSphere(name + '-loop' + side, { diameter: 0.3, segments: 10 }, scene)
        loop.scaling.set(1, 0.7, 0.45)
        loop.position.set(side * 0.17, 0.06, 0.1)
        loop.rotation.z = side * 0.35
        loop.material = ribbon
        loop.parent = hat
      }
      const knot = MeshBuilder.CreateSphere(name + '-knot', { diameter: 0.14, segments: 8 }, scene)
      knot.position.set(0, 0.06, 0.13)
      knot.material = ribbon
      knot.parent = hat
      hat.rotation.x = -0.35
    } else if (id === 'hat-crown') {
      const gold = material(scene, name + '-gold', '#F4CF63')
      gold.emissiveColor = new Color3(0.18, 0.13, 0.02)
      const band = MeshBuilder.CreateCylinder(
        name + '-band',
        { height: 0.18, diameterTop: 0.46, diameterBottom: 0.4, tessellation: 18 },
        scene,
      )
      band.position.y = 0.05
      band.material = gold
      band.parent = hat
      for (let i = 0; i < 5; i++) {
        const angle = (i / 5) * Math.PI * 2
        const tip = MeshBuilder.CreateSphere(name + '-tip' + i, { diameter: 0.09, segments: 6 }, scene)
        tip.position.set(Math.sin(angle) * 0.22, 0.17, Math.cos(angle) * 0.22)
        tip.material = gold
        tip.parent = hat
      }
    }
    parts.hat = hat
  }

  // 衣服颜色是一条围巾；传空就不围
  function setOutfit(id) {
    parts.scarf?.dispose(false, true)
    parts.scarf = null
    const color = OUTFIT_COLORS[id]
    if (!color) return
    const cloth = material(scene, name + '-cloth', color)
    const scarf = new TransformNode(name + '-scarf', scene)
    scarf.parent = body
    const ring = MeshBuilder.CreateTorus(name + '-ring', { diameter: 1.0, thickness: 0.15, tessellation: 28 }, scene)
    ring.position.y = -0.3
    ring.rotation.x = 0.12
    ring.material = cloth
    ring.parent = scarf
    const tail = MeshBuilder.CreateBox(name + '-tail', { width: 0.16, height: 0.3, depth: 0.06 }, scene)
    tail.position.set(0.22, -0.42, -0.42)
    tail.rotation.set(0.3, 0, -0.25)
    tail.material = cloth
    tail.parent = scarf
    parts.scarf = scarf
  }

  // 走路时身子一颠一颠，脚前后交替；speed 是 0 到 1
  function animate(time, speed, airborne) {
    const step = Math.sin(time * 12) * speed
    body.position.y = DANGO_RADIUS + Math.abs(step) * 0.1
    const squash = airborne ? 1.08 : 1 - Math.abs(step) * 0.06 + Math.sin(time * 2.2) * 0.015
    body.scaling.set(2 - squash, squash, 2 - squash)
    feet[0].position.z = 0.05 + step * 0.22
    feet[1].position.z = 0.05 - step * 0.22
  }

  setFace(Face.NORMAL)
  return { root, meshes, setFace, setHat, setOutfit, animate }
}
