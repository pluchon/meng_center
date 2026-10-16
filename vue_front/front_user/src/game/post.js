// 画面后处理：按深度描出物体轮廓（远处慢慢淡掉），再做一次抗锯齿；画质档位决定开哪些
import { PostProcess } from '@babylonjs/core/PostProcesses/postProcess'
import { FxaaPostProcess } from '@babylonjs/core/PostProcesses/fxaaPostProcess'
import { Effect } from '@babylonjs/core/Materials/effect'
import '@babylonjs/core/Rendering/depthRendererSceneComponent'

// 三档画质：渲染分辨率、阴影、描边、角色周围花草的半径和密度
export const QUALITY = Object.freeze({
  high: { scale: 1, shadow: 2048, outline: true, floraRadius: 60, tufts: 5 },
  medium: { scale: 1, shadow: 1024, outline: true, floraRadius: 48, tufts: 4 },
  low: { scale: 1.5, shadow: 512, outline: false, floraRadius: 34, tufts: 2 },
})

Effect.ShadersStore.mengOutlineFragmentShader = `
precision highp float;
varying vec2 vUV;
uniform sampler2D textureSampler;
uniform sampler2D depthSampler;
uniform vec2 texel;
uniform float far;
uniform vec3 lineColor;
void main() {
  vec4 c = texture2D(textureSampler, vUV);
  float d = texture2D(depthSampler, vUV).r;
  float l = texture2D(depthSampler, vUV - vec2(texel.x, 0.0)).r;
  float r = texture2D(depthSampler, vUV + vec2(texel.x, 0.0)).r;
  float u = texture2D(depthSampler, vUV - vec2(0.0, texel.y)).r;
  float b = texture2D(depthSampler, vUV + vec2(0.0, texel.y)).r;
  float z = min(d, min(min(l, r), min(u, b))) * far;
  // 用二阶差分：斜着看的平地深度是线性变化的，二阶差分接近 0，只有轮廓处才大
  float diff = max(abs(l + r - 2.0 * d), abs(u + b - 2.0 * d)) * far;
  float edge = smoothstep(z * 0.03 + 0.12, z * 0.06 + 0.3, diff) * (1.0 - smoothstep(110.0, 220.0, z));
  gl_FragColor = vec4(mix(c.rgb, c.rgb * lineColor, edge * 0.85), c.a);
}`

// 只有交给 outline 的网格才进深度图：地面、建筑、树、石头和角色；随风摆的花草、水和天空不描边。
// 用固定名单而不是每帧筛一遍场景，深度图那一遍渲染能省下大半
export function createPost(scene, camera, quality) {
  let map = null
  scene.imageProcessingConfiguration.vignetteEnabled = true
  scene.imageProcessingConfiguration.vignetteWeight = 1.1
  scene.imageProcessingConfiguration.contrast = 1.08
  if (quality.outline) {
    const depth = scene.enableDepthRenderer(camera, false)
    map = depth.getDepthMap()
    map.renderList = []
    const outline = new PostProcess('outline', 'mengOutline', ['texel', 'far', 'lineColor'], ['depthSampler'], 1, camera)
    outline.onApply = (effect) => {
      effect.setTexture('depthSampler', map)
      effect.setFloat2('texel', 1 / outline.width, 1 / outline.height)
      effect.setFloat('far', camera.maxZ)
      effect.setFloat3('lineColor', 0.3, 0.42, 0.5)
    }
  }
  new FxaaPostProcess('fxaa', 1, camera)
  return {
    outline(meshes) {
      if (map) map.renderList = meshes
    },
  }
}
