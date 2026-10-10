// Midpoint backtracing carries velocity and dye through the pressure-projected flow.
const transport = `
vec2 backtrace(vec2 uv) {
  vec2 motion = texture2D(velocity, uv).xy;
  vec2 halfUv = clamp(uv - motion * dt * 0.5 / aspect, pixel, 1.0 - pixel);
  vec2 halfway = texture2D(velocity, halfUv).xy;
  return clamp(uv - halfway * dt / aspect, pixel, 1.0 - pixel);
}
float brushWeight(vec2 uv) {
  vec2 offset = (uv - previousPointer) * aspect;
  vec2 segment = (pointer - previousPointer) * aspect;
  float along = clamp(dot(offset, segment) / max(dot(segment, segment), 0.000001), 0.0, 1.0);
  vec2 nearest = offset - segment * along;
  return exp(-dot(nearest, nearest) / (radius * radius));
}`
export const vertexShader = `
varying vec2 vUv;
void main() {
  vUv = uv;
  gl_Position = vec4(position.xy, 0.0, 1.0);
}`
export const advectionShader = `
uniform sampler2D velocity;
uniform vec2 aspect;
uniform vec2 pointer;
uniform vec2 force;
uniform vec2 previousPointer;
uniform vec2 pixel;
uniform float dt;
uniform float radius;
uniform float velocityDecay;
varying vec2 vUv;
${transport}
void main() {
  vec2 next = texture2D(velocity, backtrace(vUv)).xy * exp(-dt * velocityDecay);
  next += force * brushWeight(vUv);
  vec2 edge = smoothstep(vec2(0.0), vec2(0.035), vUv) * smoothstep(vec2(0.0), vec2(0.035), 1.0 - vUv);
  gl_FragColor = vec4(next * edge.x * edge.y, 0.0, 1.0);
}`
export const curlShader = `
uniform sampler2D velocity;
uniform vec2 pixel;
varying vec2 vUv;
void main() {
  float left = texture2D(velocity, vUv - vec2(pixel.x, 0.0)).y;
  float right = texture2D(velocity, vUv + vec2(pixel.x, 0.0)).y;
  float bottom = texture2D(velocity, vUv - vec2(0.0, pixel.y)).x;
  float top = texture2D(velocity, vUv + vec2(0.0, pixel.y)).x;
  gl_FragColor = vec4(0.5 * (right - left - top + bottom), 0.0, 0.0, 1.0);
}`
export const vorticityShader = `
uniform sampler2D velocity;
uniform sampler2D curl;
uniform vec2 pixel;
uniform float dt;
uniform float vorticity;
uniform float maxSpeed;
varying vec2 vUv;
void main() {
  float left = abs(texture2D(curl, vUv - vec2(pixel.x, 0.0)).r);
  float right = abs(texture2D(curl, vUv + vec2(pixel.x, 0.0)).r);
  float bottom = abs(texture2D(curl, vUv - vec2(0.0, pixel.y)).r);
  float top = abs(texture2D(curl, vUv + vec2(0.0, pixel.y)).r);
  vec2 gradient = vec2(right - left, top - bottom);
  gradient /= max(length(gradient), 0.00001);
  float spin = texture2D(curl, vUv).r;
  vec2 next = texture2D(velocity, vUv).xy + vec2(gradient.y, -gradient.x) * spin * vorticity * dt;
  next *= min(1.0, maxSpeed / max(length(next), 0.00001));
  gl_FragColor = vec4(next, 0.0, 1.0);
}`
export const divergenceShader = `
uniform sampler2D velocity;
uniform vec2 pixel;
varying vec2 vUv;
void main() {
  float left = texture2D(velocity, vUv - vec2(pixel.x, 0.0)).x;
  float right = texture2D(velocity, vUv + vec2(pixel.x, 0.0)).x;
  float bottom = texture2D(velocity, vUv - vec2(0.0, pixel.y)).y;
  float top = texture2D(velocity, vUv + vec2(0.0, pixel.y)).y;
  gl_FragColor = vec4(0.5 * (right - left + top - bottom), 0.0, 0.0, 1.0);
}`
export const pressureShader = `
uniform sampler2D pressure;
uniform sampler2D divergence;
uniform vec2 pixel;
varying vec2 vUv;
void main() {
  float left = texture2D(pressure, vUv - vec2(pixel.x, 0.0)).r;
  float right = texture2D(pressure, vUv + vec2(pixel.x, 0.0)).r;
  float bottom = texture2D(pressure, vUv - vec2(0.0, pixel.y)).r;
  float top = texture2D(pressure, vUv + vec2(0.0, pixel.y)).r;
  float value = (left + right + bottom + top - texture2D(divergence, vUv).r) * 0.25;
  gl_FragColor = vec4(value, 0.0, 0.0, 1.0);
}`
export const projectionShader = `
uniform sampler2D pressure;
uniform sampler2D velocity;
uniform vec2 pixel;
varying vec2 vUv;
void main() {
  float left = texture2D(pressure, vUv - vec2(pixel.x, 0.0)).r;
  float right = texture2D(pressure, vUv + vec2(pixel.x, 0.0)).r;
  float bottom = texture2D(pressure, vUv - vec2(0.0, pixel.y)).r;
  float top = texture2D(pressure, vUv + vec2(0.0, pixel.y)).r;
  vec2 next = texture2D(velocity, vUv).xy - 0.5 * vec2(right - left, top - bottom);
  gl_FragColor = vec4(next, 0.0, 1.0);
}`
export const dyeShader = `
uniform sampler2D velocity;
uniform sampler2D dye;
uniform vec2 pixel;
uniform vec2 aspect;
uniform vec2 pointer;
uniform vec2 previousPointer;
uniform vec2 force;
uniform float dt;
uniform float radius;
uniform float dyeDecay;
varying vec2 vUv;
${transport}
void main() {
  float concentration = texture2D(dye, backtrace(vUv)).r * exp(-dt * dyeDecay);
  float deposit = min(length(force) * 2.0, 0.35) * brushWeight(vUv);
  gl_FragColor = vec4(min(concentration + deposit, 1.0), 0.0, 0.0, 1.0);
}`
export const colorShader = `
uniform sampler2D dye;
uniform sampler2D previousDye;
uniform float blend;
uniform vec3 colorBlue;
varying vec2 vUv;
void main() {
  float concentration = clamp(mix(texture2D(previousDye, vUv).r, texture2D(dye, vUv).r, blend), 0.0, 1.0);
  gl_FragColor = vec4(colorBlue, smoothstep(0.03, 1.0, concentration) * 0.75);
}`
