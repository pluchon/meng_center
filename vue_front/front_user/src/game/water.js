// 海和湖：一整块水面，按水下地形的深浅从浅绿过渡到深蓝，岸边有一圈圈往外推的白浪，迎着太阳会闪
import { MeshBuilder } from '@babylonjs/core/Meshes/meshBuilder'
import { ShaderMaterial } from '@babylonjs/core/Materials/shaderMaterial'
import { Effect } from '@babylonjs/core/Materials/effect'
import { WATER, WORLD } from './terrain'

Effect.ShadersStore.mengWaterVertexShader = `
precision highp float;
attribute vec3 position;
uniform mat4 world;
uniform mat4 viewProjection;
varying vec3 vPos;
void main() {
  vec4 p = world * vec4(position, 1.0);
  vPos = p.xyz;
  gl_Position = viewProjection * p;
}`

Effect.ShadersStore.mengWaterFragmentShader = `
precision highp float;
varying vec3 vPos;
uniform sampler2D heightSampler;
uniform float heightSize;
uniform float worldHalf;
uniform float time;
uniform vec3 cameraPos;
uniform vec3 toSun;
uniform vec3 sunColor;
uniform vec3 skyColor;
uniform vec3 fogColor;
uniform float fogDensity;
uniform float light;

// 高度表是浮点纹理，用最近点采样再手动双线性插值，各种显卡都能用
float terrain(vec2 xz) {
  vec2 f = (xz + worldHalf) / (2.0 * worldHalf) * (heightSize - 1.0);
  if (f.x < 0.0 || f.y < 0.0 || f.x > heightSize - 1.0 || f.y > heightSize - 1.0) return -24.0;
  vec2 i = floor(f);
  vec2 t = f - i;
  float a = texture2D(heightSampler, (i + 0.5) / heightSize).r;
  float b = texture2D(heightSampler, (i + vec2(1.5, 0.5)) / heightSize).r;
  float c = texture2D(heightSampler, (i + vec2(0.5, 1.5)) / heightSize).r;
  float d = texture2D(heightSampler, (i + 1.5) / heightSize).r;
  return mix(mix(a, b, t.x), mix(c, d, t.x), t.y);
}

float wave(vec2 p) {
  return sin(p.x * 0.31 + time * 1.2) * 0.5 + sin(p.y * 0.37 - time * 0.9) * 0.5
    + sin((p.x + p.y) * 0.83 + time * 1.7) * 0.25 + sin((p.x - p.y) * 1.31 - time * 2.1) * 0.12;
}

void main() {
  float depth = max(0.0, -terrain(vPos.xz));
  vec2 p = vPos.xz;
  float e = 0.2;
  vec3 n = normalize(vec3(-(wave(p + vec2(e, 0.0)) - wave(p - vec2(e, 0.0))) * 0.12, 1.0, -(wave(p + vec2(0.0, e)) - wave(p - vec2(0.0, e))) * 0.12));
  vec3 v = normalize(cameraPos - vPos);
  float fresnel = pow(1.0 - max(dot(n, v), 0.0), 3.0);
  vec3 shallow = vec3(0.52, 0.87, 0.84);
  vec3 deep = vec3(0.13, 0.48, 0.68);
  vec3 col = mix(shallow, deep, smoothstep(0.3, 10.0, depth));
  col = mix(col, skyColor, fresnel * 0.5);
  float spec = pow(max(dot(reflect(-toSun, n), v), 0.0), 90.0);
  col += sunColor * step(0.45, spec) * 0.7;
  // 岸边白浪：贴着岸的一圈，加上往外推的条纹
  float band = step(0.72, fract(depth * 0.7 - time * 0.22 + wave(p * 0.5) * 0.1)) * (1.0 - smoothstep(0.0, 2.6, depth));
  float foam = max(1.0 - smoothstep(0.0, 0.45, depth), band * 0.75);
  col = mix(col, vec3(1.0), foam * 0.9);
  col *= light;
  float dist = length(cameraPos - vPos);
  float fog = clamp(exp(-pow(fogDensity * dist, 2.0)), 0.0, 1.0);
  col = mix(fogColor, col, fog);
  float alpha = max(mix(0.72, 0.94, smoothstep(0.0, 5.0, depth)), foam);
  gl_FragColor = vec4(col, alpha);
}`

export function createWater(scene, heightTexture, heightSize) {
  const material = new ShaderMaterial('water', scene, { vertex: 'mengWater', fragment: 'mengWater' }, {
    attributes: ['position'],
    uniforms: ['world', 'viewProjection', 'heightSize', 'worldHalf', 'time', 'cameraPos', 'toSun', 'sunColor', 'skyColor', 'fogColor', 'fogDensity', 'light'],
    samplers: ['heightSampler'],
    needAlphaBlending: true,
  })
  material.setTexture('heightSampler', heightTexture)
  material.setFloat('heightSize', heightSize)
  material.setFloat('worldHalf', WORLD.HALF)
  material.backFaceCulling = false
  const sea = MeshBuilder.CreateGround('water', { width: 7000, height: 7000, subdivisions: 4 }, scene)
  sea.position.y = WATER
  sea.material = material
  sea.isPickable = false
  return function update(sky, time, camera, fogDensity, light) {
    material.setFloat('time', time)
    material.setVector3('cameraPos', camera.globalPosition)
    material.setVector3('toSun', sky.toSun)
    material.setColor3('sunColor', sky.sunColor)
    material.setColor3('skyColor', sky.horizon)
    material.setColor3('fogColor', sky.fog)
    material.setFloat('fogDensity', fogDensity)
    material.setFloat('light', light)
  }
}
