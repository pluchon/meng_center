// 点位上的东西：金币和星之碎片走近自动拾取，宝箱按 E 打开
import { MeshBuilder } from '@babylonjs/core/Meshes/meshBuilder'
import { Mesh } from '@babylonjs/core/Meshes/mesh'
import { TransformNode } from '@babylonjs/core/Meshes/transformNode'
import { StandardMaterial } from '@babylonjs/core/Materials/standardMaterial'
import { Texture } from '@babylonjs/core/Materials/Textures/texture'
import { Color3 } from '@babylonjs/core/Maths/math.color'
import { Vector4 } from '@babylonjs/core/Maths/math.vector'
import '@babylonjs/core/Meshes/instancedMesh'
import { SpotKind } from '@/constants/game'
import { heightAt } from './terrain'

// 自动拾取的距离，比后端允许的 3 个单位小，留出误差
const TOUCH = 1.5
// 宝箱可以打开的距离
const REACH = 2.4

function sprite(scene, name, url, size, glow) {
  const texture = new Texture(url, scene)
  texture.hasAlpha = true
  const m = new StandardMaterial(name, scene)
  m.diffuseTexture = texture
  m.emissiveColor = glow
  m.specularColor = Color3.Black()
  m.backFaceCulling = false
  const mesh = MeshBuilder.CreatePlane(name, { size, sideOrientation: Mesh.DOUBLESIDE }, scene)
  mesh.material = m
  mesh.isPickable = false
  return mesh
}

// 宝箱：箱体和箱盖各贴图片的下半和上半，打开时箱盖绕后沿翻起来
function createChest(scene, material, spot, index) {
  const root = new TransformNode('chest' + index, scene)
  root.position.set(spot.x, heightAt(spot.x, spot.z) - 0.05, spot.z)
  root.rotation.y = Math.atan2(spot.x, spot.z)
  const side = new Vector4(0.15, 0.2, 0.25, 0.3)
  const body = MeshBuilder.CreateBox(
    'chest-body' + index,
    { width: 1.3, height: 0.75, depth: 0.9, faceUV: [side, new Vector4(0.08, 0.1, 0.92, 0.58), side, side, side, side] },
    scene,
  )
  body.position.y = 0.375
  body.material = material
  body.parent = root
  const hinge = new TransformNode('chest-hinge' + index, scene)
  hinge.parent = root
  hinge.position.set(0, 0.75, 0.45)
  const lid = MeshBuilder.CreateBox(
    'chest-lid' + index,
    { width: 1.34, height: 0.36, depth: 0.94, faceUV: [side, new Vector4(0.08, 0.58, 0.92, 0.9), side, side, side, side] },
    scene,
  )
  lid.position.set(0, 0.18, -0.47)
  lid.material = material
  lid.parent = hinge
  return { root, hinge, meshes: [body, lid] }
}

export function createPickups(scene, urls, spots, casters) {
  const coin = sprite(scene, 'coin', urls.coin, 1.1, new Color3(0.35, 0.3, 0.1))
  // 实例跟着源网格画，源网格本身藏到海底下
  coin.position.y = -100
  const chestMaterial = new StandardMaterial('chest', scene)
  chestMaterial.diffuseTexture = new Texture(urls.chest, scene)
  chestMaterial.specularColor = new Color3(0.08, 0.08, 0.08)

  const items = spots.map((spot, index) => {
    const item = { spot, taken: spot.taken, pending: false, open: 0 }
    const y = heightAt(spot.x, spot.z)
    if (spot.kind === SpotKind.COIN) {
      item.mesh = coin.createInstance('coin' + index)
      item.mesh.position.set(spot.x, y + 0.9, spot.z)
    } else if (spot.kind === SpotKind.STAR) {
      item.mesh = sprite(scene, 'star' + index, urls.star, 1.5, new Color3(0.8, 0.75, 0.5))
      item.mesh.billboardMode = Mesh.BILLBOARDMODE_ALL
      item.mesh.position.set(spot.x, y + 1.2, spot.z)
    } else {
      item.chest = createChest(scene, chestMaterial, spot, index)
      casters.push(...item.chest.meshes)
      item.open = spot.taken ? 1 : 0
    }
    if (item.mesh) item.mesh.setEnabled(!spot.taken)
    return item
  })
  const byId = new Map(items.map((i) => [i.spot.id, i]))

  function update(time, dt, x, y, z, touch) {
    let nearChest = null
    for (const item of items) {
      const { spot } = item
      const dx = x - spot.x
      const dz = z - spot.z
      const d = Math.hypot(dx, dz)
      if (item.mesh && !item.taken) {
        item.mesh.rotation.y = spot.kind === SpotKind.COIN ? time * 2.4 : 0
        item.mesh.position.y = heightAt(spot.x, spot.z) + (spot.kind === SpotKind.STAR ? 1.2 : 0.9) + Math.sin(time * 2 + spot.x) * 0.12
        if (!item.pending && d < TOUCH && Math.abs(y - heightAt(spot.x, spot.z)) < 2.5) touch(item)
      }
      if (item.chest) {
        item.chest.hinge.rotation.x = -1.9 * item.open
        if (item.taken && item.open < 1) item.open = Math.min(1, item.open + dt * 2.5)
        if (!item.taken && !item.pending && d < REACH && (!nearChest || d < nearChest.d)) nearChest = { item, d }
      }
    }
    return nearChest?.item ?? null
  }

  // 拾取在路上时先藏起来；失败了再放回去
  function hold(item) {
    item.pending = true
    item.mesh?.setEnabled(false)
  }

  function settle(item, ok) {
    item.pending = false
    if (ok) item.taken = true
    else item.mesh?.setEnabled(!item.taken)
  }

  return { update, hold, settle, byId }
}
