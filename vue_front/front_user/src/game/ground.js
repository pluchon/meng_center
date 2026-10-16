// 地面：整张高度表切成 64 米见方的块，每块三档精度，离得远的用粗网格；块边往下垂一圈裙边，粗细相接处不露缝
import { Mesh } from '@babylonjs/core/Meshes/mesh'
import { VertexData } from '@babylonjs/core/Meshes/mesh.vertexData'
import { StandardMaterial } from '@babylonjs/core/Materials/standardMaterial'
import { Texture } from '@babylonjs/core/Materials/Textures/texture'
import { RawTexture } from '@babylonjs/core/Materials/Textures/rawTexture'
import { Constants } from '@babylonjs/core/Engines/constants'
import { Color3 } from '@babylonjs/core/Maths/math.color'
import { SURFACE_SIZE, WORLD, buildSurface, buildTerrain, heightAt } from './terrain'
import { addTerrain, addToon } from './materials'

const CHUNK = 64
// 三档格距和切换距离
const LODS = Object.freeze([
  { step: 2, until: 150 },
  { step: 4, until: 320 },
  { step: 8, until: Infinity },
])
const SKIRT = 4

function tiled(url, scene) {
  const t = new Texture(url, scene)
  t.anisotropicFilteringLevel = 8
  return t
}

// 高度表做成浮点纹理，水面和草地的着色器要查水深和地面高度
export function createHeightTexture(scene) {
  const grid = buildTerrain()
  const size = Math.round(Math.sqrt(grid.length))
  const texture = RawTexture.CreateRTexture(grid, size, size, scene, false, false, Texture.NEAREST_SAMPLINGMODE, Constants.TEXTURETYPE_FLOAT)
  texture.wrapU = Texture.CLAMP_ADDRESSMODE
  texture.wrapV = Texture.CLAMP_ADDRESSMODE
  return { texture, size }
}

// 混合图：r 沙滩、g 小路、b 森林地面、a 花海
function createSplat(scene) {
  const texture = RawTexture.CreateRGBATexture(buildSurface(), SURFACE_SIZE, SURFACE_SIZE, scene, false, false, Texture.BILINEAR_SAMPLINGMODE)
  texture.wrapU = Texture.CLAMP_ADDRESSMODE
  texture.wrapV = Texture.CLAMP_ADDRESSMODE
  return texture
}

function normalAt(x, z) {
  const e = 2
  const nx = heightAt(x - e, z) - heightAt(x + e, z)
  const nz = heightAt(x, z - e) - heightAt(x, z + e)
  const len = Math.hypot(nx, 2 * e, nz)
  return [nx / len, (2 * e) / len, nz / len]
}

// 一块地的一档网格，外加四边的裙边
function chunkMesh(scene, name, x0, z0, step) {
  const n = CHUNK / step
  const positions = []
  const normals = []
  const indices = []
  for (let j = 0; j <= n; j++)
    for (let i = 0; i <= n; i++) {
      const x = x0 + i * step
      const z = z0 + j * step
      positions.push(x, heightAt(x, z), z)
      normals.push(...normalAt(x, z))
    }
  const at = (i, j) => j * (n + 1) + i
  for (let j = 0; j < n; j++)
    for (let i = 0; i < n; i++) indices.push(at(i, j), at(i, j + 1), at(i + 1, j), at(i + 1, j), at(i, j + 1), at(i + 1, j + 1))
  // 裙边：沿四条边各复制一排顶点往下拉
  const edges = [
    Array.from({ length: n + 1 }, (_, i) => at(i, 0)),
    Array.from({ length: n + 1 }, (_, j) => at(n, j)),
    Array.from({ length: n + 1 }, (_, i) => at(n - i, n)),
    Array.from({ length: n + 1 }, (_, j) => at(0, n - j)),
  ]
  for (const edge of edges) {
    const start = positions.length / 3
    for (const v of edge) {
      positions.push(positions[v * 3], positions[v * 3 + 1] - SKIRT, positions[v * 3 + 2])
      normals.push(normals[v * 3], normals[v * 3 + 1], normals[v * 3 + 2])
    }
    for (let k = 0; k < edge.length - 1; k++) indices.push(edge[k], start + k, edge[k + 1], edge[k + 1], start + k, start + k + 1)
  }
  const data = new VertexData()
  Object.assign(data, { positions, normals, indices })
  const mesh = new Mesh(name, scene)
  data.applyToMesh(mesh)
  mesh.isPickable = false
  mesh.receiveShadows = true
  return mesh
}

export function createGround(scene, urls) {
  const material = new StandardMaterial('ground', scene)
  material.diffuseColor = Color3.White()
  material.specularColor = Color3.Black()
  // 裙边朝外的面也要画，否则从侧面能看见缝
  material.backFaceCulling = false
  addTerrain(material, {
    splat: createSplat(scene),
    grass: tiled(urls.grass, scene),
    sand: tiled(urls.sand, scene),
    rock: tiled(urls.rock, scene),
    path: tiled(urls.path, scene),
  })
  addToon(material)
  const chunks = []
  for (let z0 = -WORLD.HALF; z0 < WORLD.HALF; z0 += CHUNK)
    for (let x0 = -WORLD.HALF; x0 < WORLD.HALF; x0 += CHUNK) {
      const meshes = LODS.map((lod, k) => {
        const m = chunkMesh(scene, `ground-${x0}-${z0}-${k}`, x0, z0, lod.step)
        m.material = material
        return m
      })
      meshes.slice(1).forEach((m, k) => meshes[0].addLODLevel(LODS[k].until, m))
      chunks.push(meshes[0])
    }
  return { chunks, material }
}
