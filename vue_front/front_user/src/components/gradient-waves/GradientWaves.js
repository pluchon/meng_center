import { defineComponent, onMounted, onUnmounted, ref } from 'vue'

const frameInterval = 1000 / 30
const vertex = `#version 300 es
in vec2 position;
void main() { gl_Position = vec4(position, 0.0, 1.0); }
`

// 保留 Vue Bits GradientWaves 的波面、相机与配色方式，限制缓冲尺寸和绘制频率。
const fragment = `#version 300 es
precision highp float;
uniform vec2 uResolution;
uniform float uTime;
uniform vec3 uHorizon;
uniform vec3 uBody;
uniform vec3 uMist;
uniform vec2 uMouse;
out vec4 fragColor;

float hash21(vec2 p) {
  vec3 p3 = fract(vec3(p.xyx) * 0.1031);
  p3 += dot(p3, p3.yzx + 33.33);
  return fract((p3.x + p3.y) * p3.z);
}

float plasma(vec3 r, vec4 tc) {
  float mx = r.x + tc.x;
  mx += 35.0 * sin((r.y + mx) / 20.0 + tc.y);
  float my = r.y - tc.z;
  my += 20.0 * cos(r.x / 23.0 + tc.w);
  return r.z - (sin(mx * 0.6 / 7.0) * 2.5
    + sin(my * 0.54 / 3.0) * 2.5 + 5.5);
}

// 波面解析法线不增加射线追踪次数，只给坡面加一点受光和背光的差别。
vec3 waveNormal(vec3 r, vec4 tc) {
  float a = (r.y + r.x + tc.x) / 20.0 + tc.y;
  float b = r.x / 23.0 + tc.w;
  float mx = r.x + tc.x + 35.0 * sin(a);
  float my = r.y - tc.z + 20.0 * cos(b);
  float dx = 1.75 * cos(a);
  float sx = 2.5 * (0.6 / 7.0) * cos(mx * 0.6 / 7.0);
  float sy = 2.5 * (0.54 / 3.0) * cos(my * 0.54 / 3.0);
  return normalize(vec3(-(sx * (1.0 + dx) - sy * (20.0 / 23.0) * sin(b)),
                        -(sx * dx + sy), 1.0));
}

void main() {
  float time = uTime * 0.4;
  vec4 tc = time / vec4(0.130, 0.810, 0.200, 0.710);
  vec2 uv = gl_FragCoord.xy / uResolution - 0.5;
  uv.x *= uResolution.x / uResolution.y;
  uv.y = -uv.y;
  float angle = (3.14159265 / 2.3) * length(uv);
  vec2 radial = uv / max(length(uv), 0.0001);
  vec3 dir = vec3(-sin(angle) * radial.y, -sin(angle) * radial.x, -cos(angle));
  float tilt = 1.11;
  dir.xz = mat2(cos(tilt), sin(tilt), -sin(tilt), cos(tilt)) * dir.xz;
  float yaw = (uMouse.x - 0.5) * 0.2;
  float pitch = (uMouse.y - 0.5) * 0.2;
  dir.xz = mat2(cos(yaw), sin(yaw), -sin(yaw), cos(yaw)) * dir.xz;
  dir.yz = mat2(cos(pitch), -sin(pitch), sin(pitch), cos(pitch)) * dir.yz;
  vec3 camera = vec3(0.0, 0.0, 30.0);
  float dist = 0.0;
  for (int i = 0; i < 70; i++) {
    float scene = plasma(camera + dist * dir, tc);
    if (abs(scene) < 0.1) break;
    dist += 0.9 * scene;
    if (!(abs(dist) < 20000.0)) { dist = 20000.0; break; }
  }
  vec3 pos = camera + dist * dir;
  float fog = clamp(15.0 / max(dist, 0.001), 0.0, 1.0);
  // 波高 0.5 到 10.5，归一成谷 0、脊 1：谷里是浅雾，脊上是深色，脊线压在后面更远更淡的坡面上。
  float height = clamp((pos.z - 0.5) / 10.0, 0.0, 1.0);
  vec3 body = mix(uMist, uBody, smoothstep(0.15, 1.0, height));
  vec3 light = normalize(vec3(-0.55, -0.35, 0.76));
  body *= 0.92 + 0.14 * dot(waveNormal(pos, tc), light);
  // 颜色随距离慢慢偏向远景色，透明度到很远才收掉，远处的脊线仍然看得见。
  vec3 color = mix(uHorizon, body, smoothstep(0.03, 0.5, fog));
  float grain = hash21(gl_FragCoord.xy + mod(uTime, 64.0) * 11.0);
  float alpha = clamp(smoothstep(0.0, 0.2, fog) + (grain - 0.5) * 0.025, 0.0, 1.0);
  fragColor = vec4(color * alpha, alpha);
}
`

export default defineComponent({
  name: 'GradientWaves',
  setup() {
    const host = ref(null),
      ready = ref(false)
    let renderer, geometry, program, mesh, resizeObserver, motion
    let frame = 0,
      previousTime = null,
      elapsed = 0,
      disposed = false
    const mouse = new Float32Array([0.5, 0.5])
    const targetMouse = [0.5, 0.5]

    function pointerMove(event) {
      if (event.pointerType !== 'mouse' || motion?.matches || document.hidden) return
      targetMouse[0] = event.clientX / window.innerWidth
      targetMouse[1] = 1 - event.clientY / window.innerHeight
    }
    function resetPointer() {
      targetMouse[0] = targetMouse[1] = 0.5
    }

    function draw() {
      if (!ready.value || renderer.gl.isContextLost()) return
      program.uniforms.uTime.value = elapsed
      renderer.render({ scene: mesh })
    }
    function animate(time) {
      frame = 0
      if (previousTime === null || time - previousTime >= frameInterval - 1) {
        if (previousTime !== null) elapsed += Math.min((time - previousTime) / 1000, 0.1)
        previousTime = time
        mouse[0] += 0.1 * (targetMouse[0] - mouse[0])
        mouse[1] += 0.1 * (targetMouse[1] - mouse[1])
        draw()
      }
      frame = requestAnimationFrame(animate)
    }
    function updatePlayback() {
      cancelAnimationFrame(frame)
      frame = 0
      previousTime = null
      if (disposed || !ready.value || document.hidden) return
      if (motion.matches) {
        resetPointer()
        mouse[0] = mouse[1] = 0.5
      }
      draw()
      if (!motion.matches) frame = requestAnimationFrame(animate)
    }
    function resize() {
      if (!renderer || disposed || !host.value) return
      const { width, height } = host.value.getBoundingClientRect()
      if (!width || !height) return
      const edge = width <= 640 ? 512 : 1280
      renderer.dpr = Math.min(1, edge / Math.max(width, height))
      renderer.setSize(width, height)
      program.uniforms.uResolution.value[0] = renderer.gl.drawingBufferWidth
      program.uniforms.uResolution.value[1] = renderer.gl.drawingBufferHeight
      if (!document.hidden) draw()
    }
    function contextLost(event) {
      event.preventDefault()
      release()
    }
    function release() {
      cancelAnimationFrame(frame)
      frame = 0
      resizeObserver?.disconnect()
      motion?.removeEventListener('change', updatePlayback)
      document.removeEventListener('visibilitychange', updatePlayback)
      window.removeEventListener('pointermove', pointerMove)
      document.documentElement.removeEventListener('pointerleave', resetPointer)
      renderer?.gl.canvas.removeEventListener('webglcontextlost', contextLost)
      geometry?.remove()
      program?.remove()
      renderer?.gl.getExtension('WEBGL_lose_context')?.loseContext()
      renderer?.gl.canvas.remove()
      renderer = geometry = program = mesh = null
      ready.value = false
    }

    onMounted(async () => {
      try {
        const { Color, Mesh, Program, Renderer, Triangle } = await import('ogl')
        if (disposed) return
        renderer = new Renderer({
          webgl: 2,
          alpha: true,
          depth: false,
          stencil: false,
          antialias: false,
          premultipliedAlpha: true,
          powerPreference: 'low-power',
        })
        if (!renderer.isWebgl2) {
          release()
          return
        }
        const gl = renderer.gl
        gl.clearColor(0, 0, 0, 0)
        geometry = new Triangle(gl)
        const styles = getComputedStyle(document.documentElement)
        const palette = (name) => new Color(styles.getPropertyValue(name).trim())
        program = new Program(gl, {
          vertex,
          fragment,
          depthTest: false,
          depthWrite: false,
          cullFace: false,
          uniforms: {
            uTime: { value: 0 },
            uResolution: { value: new Float32Array([1, 1]) },
            uHorizon: { value: palette('--meng-growth-wave-horizon') },
            uBody: { value: palette('--meng-growth-wave-body') },
            uMist: { value: palette('--meng-growth-wave-mist') },
            uMouse: { value: mouse },
          },
        })
        if (!gl.getProgramParameter(program.program, gl.LINK_STATUS)) {
          release()
          return
        }
        mesh = new Mesh(gl, { geometry, program })
        host.value.appendChild(gl.canvas)
        gl.canvas.addEventListener('webglcontextlost', contextLost)
        ready.value = true
        motion = matchMedia('(prefers-reduced-motion: reduce)')
        motion.addEventListener('change', updatePlayback)
        document.addEventListener('visibilitychange', updatePlayback)
        window.addEventListener('pointermove', pointerMove, { passive: true })
        document.documentElement.addEventListener('pointerleave', resetPointer)
        resizeObserver = new ResizeObserver(resize)
        resizeObserver.observe(host.value)
        resize()
        updatePlayback()
      } catch {
        release()
      }
    })
    onUnmounted(() => {
      disposed = true
      release()
    })
    return { host, ready }
  },
})
