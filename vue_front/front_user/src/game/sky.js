// 天空和昼夜：渐变天幕、太阳和月亮、夜里的星星、慢慢飘的卡通云；时间推动太阳的位置和全场的光色
import { MeshBuilder } from '@babylonjs/core/Meshes/meshBuilder'
import { Mesh } from '@babylonjs/core/Meshes/mesh'
import { ShaderMaterial } from '@babylonjs/core/Materials/shaderMaterial'
import { Effect } from '@babylonjs/core/Materials/effect'
import { Color3 } from '@babylonjs/core/Maths/math.color'
import { Vector3 } from '@babylonjs/core/Maths/math.vector'

// 一整天的真实秒数；进游戏时是早上八点
export const DAY_SECONDS = 1200
export const START_HOUR = 8

Effect.ShadersStore.mengSkyVertexShader = `
precision highp float;
attribute vec3 position;
uniform mat4 worldViewProjection;
varying vec3 vDir;
void main() {
  vDir = position;
  gl_Position = worldViewProjection * vec4(position, 1.0);
}`

Effect.ShadersStore.mengSkyFragmentShader = `
precision highp float;
varying vec3 vDir;
uniform vec3 toSun;
uniform vec3 zenith;
uniform vec3 horizon;
uniform vec3 sunTint;
uniform vec3 cloudTint;
uniform float night;
uniform float time;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}
float fbm(vec2 p) {
  float s = 0.0;
  float a = 0.55;
  for (int i = 0; i < 4; i++) { s += noise(p) * a; p *= 2.02; a *= 0.5; }
  return s;
}

void main() {
  vec3 d = normalize(vDir);
  float h = d.y;
  vec3 col = mix(horizon, zenith, pow(smoothstep(-0.05, 0.75, h), 0.7));
  // 地平线以下和雾同色，从高处往远看海面和天接得上
  col = mix(col, horizon, smoothstep(0.0, -0.05, h));

  // 太阳：柔光晕加一个圆盘；月亮在对面
  float sd = dot(d, toSun);
  col += sunTint * pow(max(sd, 0.0), 10.0) * 0.45 * (1.0 - night);
  col = mix(col, vec3(1.0, 0.98, 0.9), smoothstep(0.9990, 0.9994, sd) * (1.0 - night));
  float md = dot(d, -toSun);
  col = mix(col, vec3(0.93, 0.95, 1.0), smoothstep(0.9993, 0.9996, md) * night);
  col += vec3(0.5, 0.6, 0.8) * pow(max(md, 0.0), 40.0) * 0.25 * night;

  // 星星：按方向分格，每格最多一颗，会轻轻闪
  if (night > 0.01 && h > 0.0) {
    vec2 cell = floor(vec2(atan(d.z, d.x) * 120.0, h * 110.0));
    float star = step(0.985, hash(cell));
    float twinkle = 0.6 + 0.4 * sin(time * 2.0 + hash(cell + 3.1) * 40.0);
    col += vec3(star * twinkle * night * smoothstep(0.0, 0.25, h));
  }

  // 云：投到一块天花板上的噪声，切成边缘干净的卡通云
  if (h > 0.0) {
    vec2 uv = d.xz / (h + 0.18) * 1.3 + vec2(time * 0.004, time * 0.0015);
    float n = fbm(uv);
    float c = smoothstep(0.56, 0.6, n);
    float shade = smoothstep(0.6, 0.72, fbm(uv + vec2(0.04, 0.03)));
    vec3 cloud = mix(cloudTint, cloudTint * vec3(0.82, 0.86, 0.95), shade);
    col = mix(col, cloud, c * smoothstep(0.0, 0.18, h) * 0.95);
  }
  gl_FragColor = vec4(col, 1.0);
}`

const C = (hex) => Color3.FromHexString(hex)
// 几个时段的配色：天顶、地平线、雾、太阳光、云
const PALETTE = Object.freeze({
  day: { zenith: C('#5DB8EB'), horizon: C('#CDEFF9'), fog: C('#CDEFF9'), sun: C('#FFF7EA'), cloud: C('#FFFFFF') },
  dusk: { zenith: C('#7F95D6'), horizon: C('#FBC7A6'), fog: C('#F7CDB4'), sun: C('#FFC69A'), cloud: C('#FFE2D0') },
  night: { zenith: C('#101C3A'), horizon: C('#2C4675'), fog: C('#2A4370'), sun: C('#9DB4E8'), cloud: C('#4A5F8E') },
})

function smooth(from, to, value) {
  const t = Math.min(1, Math.max(0, (value - from) / (to - from)))
  return t * t * (3 - 2 * t)
}

function blend(a, b, t) {
  return Color3.Lerp(a, b, Math.min(1, Math.max(0, t)))
}

// 某个时刻的天色和光照
export function skyAt(hour) {
  // 六点日出、十八点日落，太阳从东边升起，正午偏南
  const angle = ((hour - 6) / 12) * Math.PI
  const elevation = Math.sin(angle)
  const toSun = new Vector3(Math.cos(angle) * 0.9, elevation, -0.35).normalize()
  const day = smooth(-0.14, 0.1, elevation)
  const dusk = (1 - smooth(0, 0.3, Math.abs(elevation - 0.04))) * 0.9
  const pick = (key) => blend(blend(PALETTE.night[key], PALETTE.day[key], day), PALETTE.dusk[key], dusk)
  const night = 1 - day
  return {
    toSun,
    // 太阳落下后改用月光，方向在对面，偏蓝偏暗
    lightDir: elevation > -0.02 ? toSun.scale(-1) : toSun.clone(),
    sunColor: pick('sun'),
    sunIntensity: 0.22 + 0.45 * day,
    hemiIntensity: 0.34 + 0.28 * day,
    zenith: pick('zenith'),
    horizon: pick('horizon'),
    fog: pick('fog'),
    cloud: pick('cloud'),
    night: night * night,
  }
}

export function createSky(scene) {
  const material = new ShaderMaterial('sky', scene, { vertex: 'mengSky', fragment: 'mengSky' }, {
    attributes: ['position'],
    uniforms: ['worldViewProjection', 'toSun', 'zenith', 'horizon', 'sunTint', 'cloudTint', 'night', 'time'],
  })
  material.backFaceCulling = false
  material.disableDepthWrite = true
  const dome = MeshBuilder.CreateSphere('sky', { diameter: 1800, segments: 24, sideOrientation: Mesh.BACKSIDE }, scene)
  dome.material = material
  dome.infiniteDistance = true
  dome.applyFog = false
  dome.isPickable = false
  dome.renderingGroupId = 0
  return function update(sky, time) {
    material.setVector3('toSun', sky.toSun)
    material.setColor3('zenith', sky.zenith)
    material.setColor3('horizon', sky.horizon)
    material.setColor3('sunTint', sky.sunColor)
    material.setColor3('cloudTint', sky.cloud)
    material.setFloat('night', sky.night)
    material.setFloat('time', time)
  }
}
