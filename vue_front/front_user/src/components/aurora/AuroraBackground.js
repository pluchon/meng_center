import { defineComponent, onMounted, onUnmounted, ref, watch } from 'vue'

const frameInterval = 1000 / 30
const maxBufferEdge = 768
const mobileBufferEdge = 512
const fieldSegments = 64
const paletteTokens = [
  '--meng-search-aurora-blue',
  '--meng-search-aurora-mint',
  '--meng-search-aurora-blue-soft',
]

const vertex = `#version 300 es
in vec2 position;
uniform float uTime;
uniform float uAmplitude;
uniform vec3 uColorStops[3];
out float vHeight;
out float vY;
out float vEdgeFade;
out vec3 vColor;

vec3 permute(vec3 x) { return mod(((x * 34.0) + 1.0) * x, 289.0); }
float snoise(vec2 v) {
  const vec4 C = vec4(0.211324865405187, 0.366025403784439,
                     -0.577350269189626, 0.024390243902439);
  vec2 i = floor(v + dot(v, C.yy));
  vec2 x0 = v - i + dot(i, C.xx);
  vec2 i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
  vec4 x12 = x0.xyxy + C.xxzz;
  x12.xy -= i1;
  i = mod(i, 289.0);
  vec3 p = permute(permute(i.y + vec3(0.0, i1.y, 1.0))
                  + i.x + vec3(0.0, i1.x, 1.0));
  vec3 m = max(0.5 - vec3(dot(x0,x0), dot(x12.xy,x12.xy),
                         dot(x12.zw,x12.zw)), 0.0);
  m = m * m;
  m = m * m;
  vec3 x = 2.0 * fract(p * C.www) - 1.0;
  vec3 h = abs(x) - 0.5;
  vec3 a0 = x - floor(x + 0.5);
  m *= 1.79284291400159 - 0.85373472095314 * (a0*a0 + h*h);
  vec3 g;
  g.x = a0.x * x0.x + h.x * x0.y;
  g.yz = a0.yz * x12.xz + h.yz * x12.yw;
  return 130.0 * dot(m, g);
}

void main() {
  vec2 uv = position * 0.5 + 0.5;
  vHeight = exp(snoise(vec2(uv.x * 2.0 + uTime * 0.2,
                            uTime * 0.45)) * 0.5 * uAmplitude);
  vY = uv.y;
  vEdgeFade = 0.25 + 0.75 * 4.0 * uv.x * (1.0 - uv.x);
  vColor = uv.x < 0.5
    ? mix(uColorStops[0], uColorStops[1], uv.x * 2.0)
    : mix(uColorStops[1], uColorStops[2], (uv.x - 0.5) * 2.0);
  gl_Position = vec4(position, 0.0, 1.0);
}
`

// 保留 Vue Bits Aurora 的噪声光场，噪声仅在网格顶点计算。
const fragment = `#version 300 es
precision highp float;
uniform float uBlend;
in float vHeight;
in float vY;
in float vEdgeFade;
in vec3 vColor;
out vec4 fragColor;
void main() {
  float intensity = 0.6 * (vY * 2.0 - vHeight + 0.2);
  float alpha = smoothstep(0.20 - uBlend * 0.5,
                          0.20 + uBlend * 0.5, intensity) * vEdgeFade;
  fragColor = vec4(vColor * alpha, alpha);
}
`

export default defineComponent({
  name: 'AuroraBackground',
  props: {
    colorStops: { type: Array, default: null },
    amplitude: { type: Number, default: 1 },
    blend: { type: Number, default: 0.5 },
    speed: { type: Number, default: 0.5 },
  },
  setup(props) {
    const host = ref(null),
      ready = ref(false)
    let renderer, program, geometry, mesh, Color, resizeObserver, motion
    let frame = 0,
      previousTime = null,
      elapsed = 0,
      disposed = false

    function updatePalette() {
      if (!program) return
      const styles = getComputedStyle(document.documentElement)
      const colors =
        props.colorStops ?? paletteTokens.map((key) => styles.getPropertyValue(key).trim())
      program.uniforms.uColorStops.value = colors.map((hex) => {
        const color = new Color(hex)
        return [color.r, color.g, color.b]
      })
      draw()
    }
    function draw() {
      if (!ready.value || !renderer || renderer.gl.isContextLost()) return
      program.uniforms.uTime.value = elapsed * props.speed
      program.uniforms.uAmplitude.value = props.amplitude
      program.uniforms.uBlend.value = Math.max(0.01, props.blend)
      renderer.render({ scene: mesh })
    }
    function animate(time) {
      frame = 0
      if (previousTime === null || time - previousTime >= frameInterval - 1) {
        if (previousTime !== null) elapsed += Math.min((time - previousTime) / 1000, 0.1)
        previousTime = time
        draw()
      }
      frame = requestAnimationFrame(animate)
    }
    function updatePlayback() {
      cancelAnimationFrame(frame)
      frame = 0
      previousTime = null
      if (disposed || !ready.value) return
      draw()
      if (!document.hidden && !motion.matches && props.speed > 0) {
        frame = requestAnimationFrame(animate)
      }
    }
    function resize() {
      if (!renderer || disposed || !host.value) return
      const { width, height } = host.value.getBoundingClientRect()
      if (!width || !height) return
      const bufferEdge = width < 640 ? mobileBufferEdge : maxBufferEdge
      renderer.dpr = Math.min(1, bufferEdge / Math.max(width, height))
      renderer.setSize(width, height)
      draw()
    }
    function contextLost() {
      ready.value = false
      cancelAnimationFrame(frame)
      frame = 0
      previousTime = null
    }
    function release() {
      resizeObserver?.disconnect()
      motion?.removeEventListener('change', updatePlayback)
      document.removeEventListener('visibilitychange', updatePlayback)
      cancelAnimationFrame(frame)
      renderer?.gl.canvas.removeEventListener('webglcontextlost', contextLost)
      geometry?.remove()
      program?.remove()
      renderer?.gl.getExtension('WEBGL_lose_context')?.loseContext()
      renderer?.gl.canvas.remove()
      renderer = program = geometry = mesh = null
      ready.value = false
    }
    watch(() => props.colorStops, updatePalette, { deep: true })
    watch(() => [props.amplitude, props.blend, props.speed], updatePlayback)

    onMounted(async () => {
      try {
        const { Color: OglColor, Geometry, Mesh, Program, Renderer } = await import('ogl')
        if (disposed) return
        Color = OglColor
        renderer = new Renderer({
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
        const positions = new Float32Array((fieldSegments + 1) * 4)
        for (let column = 0; column <= fieldSegments; column++) {
          const x = (column / fieldSegments) * 2 - 1
          positions.set([x, -1, x, 1], column * 4)
        }
        geometry = new Geometry(gl, { position: { size: 2, data: positions } })
        program = new Program(gl, {
          vertex,
          fragment,
          transparent: true,
          cullFace: false,
          depthTest: false,
          depthWrite: false,
          uniforms: {
            uTime: { value: 0 },
            uAmplitude: { value: props.amplitude },
            uBlend: { value: props.blend },
            uColorStops: { value: [] },
          },
        })
        if (!gl.getProgramParameter(program.program, gl.LINK_STATUS)) {
          release()
          return
        }
        mesh = new Mesh(gl, { geometry, program, mode: gl.TRIANGLE_STRIP })
        host.value.appendChild(gl.canvas)
        gl.canvas.addEventListener('webglcontextlost', contextLost)
        ready.value = true
        updatePalette()
        resize()
        resizeObserver = new ResizeObserver(resize)
        resizeObserver.observe(host.value)
        motion = matchMedia('(prefers-reduced-motion: reduce)')
        motion.addEventListener('change', updatePlayback)
        document.addEventListener('visibilitychange', updatePlayback)
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
