import {
  Color,
  GLSL3,
  Mesh,
  NoBlending,
  OrthographicCamera,
  PlaneGeometry,
  Scene,
  ShaderMaterial,
  Vector2,
  WebGLRenderer,
} from 'three'
import { vertex, fragment } from './pixelBlastShaders'

const frameInterval = 1000 / 30
const maxClicks = 10

export function createPixelBlast(host, onFailure) {
  let renderer, geometry, material, scene, camera, resizeObserver, motion, visibilityObserver
  let frame = 0,
    disposed = false,
    visible = true,
    previous = null,
    elapsed = 240,
    clickIndex = 0
  let bounds
  let uniforms
  function draw() {
    if (disposed || !renderer || renderer.getContext().isContextLost()) return
    uniforms.uTime.value = elapsed * 0.6
    uniforms.uClickPos.value.forEach((pos, i) => {
      if (pos.x >= 0 && uniforms.uTime.value - uniforms.uClickTimes.value[i] > 5) pos.set(-1, -1)
    })
    renderer.render(scene, camera)
  }
  function animate(time) {
    frame = 0
    if (previous === null || time - previous >= frameInterval - 1) {
      const delta = previous === null ? 0 : Math.min((time - previous) / 1000, 0.1)
      previous = time
      elapsed += delta
      draw()
    }
    frame = requestAnimationFrame(animate)
  }
  function playback() {
    cancelAnimationFrame(frame)
    frame = 0
    previous = null
    if (disposed || !renderer || document.hidden || !visible) return
    if (motion.matches) {
      uniforms.uClickPos.value.forEach((pos) => pos.set(-1, -1))
    }
    draw()
    if (!motion.matches) frame = requestAnimationFrame(animate)
  }
  function resize() {
    if (disposed) return
    bounds = host.getBoundingClientRect()
    const width = bounds.width,
      height = bounds.height
    if (!width || !height) return
    const edge = width <= 640 ? 768 : 1280
    const ratio = Math.min(1, edge / Math.max(width, height))
    renderer.setPixelRatio(ratio)
    renderer.setSize(width, height, false)
    const buffer = renderer.getDrawingBufferSize(new Vector2())
    uniforms.uResolution.value.copy(buffer)
    uniforms.uPixelSize.value = 6 * ratio
    if (!document.hidden) draw()
  }
  function pointer(event) {
    if (
      disposed ||
      document.hidden ||
      motion.matches ||
      !visible ||
      event.target.closest?.('[data-pixel-exclude]')
    ) {
      return null
    }
    const x = (event.clientX - bounds.left) / bounds.width,
      y = 1 - (event.clientY - bounds.top) / bounds.height
    if (x < 0 || x > 1 || y < 0 || y > 1) {
      return null
    }
    return { x, y }
  }
  function pointerClick(event) {
    const point = pointer(event)
    if (!point || event.button !== 0) return
    uniforms.uClickPos.value[clickIndex].set(
      point.x * uniforms.uResolution.value.x,
      point.y * uniforms.uResolution.value.y,
    )
    uniforms.uClickTimes.value[clickIndex] = uniforms.uTime.value
    clickIndex = (clickIndex + 1) % maxClicks
  }
  function contextLost(event) {
    event.preventDefault()
    dispose()
    onFailure()
  }
  function dispose() {
    if (disposed) return
    disposed = true
    cancelAnimationFrame(frame)
    resizeObserver?.disconnect()
    visibilityObserver?.disconnect()
    motion?.removeEventListener('change', playback)
    document.removeEventListener('visibilitychange', playback)
    window.removeEventListener('click', pointerClick)
    renderer?.domElement.removeEventListener('webglcontextlost', contextLost)
    geometry?.dispose()
    material?.dispose()
    renderer?.dispose()
    renderer?.forceContextLoss()
    renderer?.domElement.remove()
  }
  try {
    const canvas = document.createElement('canvas')
    const context = canvas.getContext('webgl2', {
      antialias: false,
      alpha: true,
      premultipliedAlpha: false,
      depth: false,
      stencil: false,
      powerPreference: 'low-power',
    })
    if (!context) throw new Error('WebGL2 unavailable')
    renderer = new WebGLRenderer({
      canvas,
      context,
      antialias: false,
      alpha: true,
      premultipliedAlpha: false,
    })
    renderer.setClearAlpha(0)
    const css = getComputedStyle(document.documentElement)
    uniforms = {
      uResolution: { value: new Vector2() },
      uTime: { value: elapsed * 0.6 },
      uColor: {
        value: new Color(
          css.getPropertyValue('--meng-message-pixel-color').trim(),
        ).convertLinearToSRGB(),
      },
      uClickPos: { value: Array.from({ length: maxClicks }, () => new Vector2(-1, -1)) },
      uClickTimes: { value: new Float32Array(maxClicks) },
      uPixelSize: { value: 6 },
      uScale: { value: 3 },
      uDensity: { value: 1.2 },
      uPixelJitter: { value: 0.5 },
      uEnableRipples: { value: 1 },
      uRippleSpeed: { value: 0.4 },
      uRippleThickness: { value: 0.12 },
      uRippleIntensity: { value: 1.5 },
      uEdgeFade: { value: 0.12 },
    }
    scene = new Scene()
    camera = new OrthographicCamera(-1, 1, 1, -1, 0, 1)
    material = new ShaderMaterial({
      vertexShader: vertex,
      fragmentShader: fragment,
      uniforms,
      transparent: true,
      blending: NoBlending,
      glslVersion: GLSL3,
      depthTest: false,
      depthWrite: false,
    })
    geometry = new PlaneGeometry(2, 2)
    scene.add(new Mesh(geometry, material))
    host.appendChild(canvas)
    canvas.addEventListener('webglcontextlost', contextLost)
    motion = matchMedia('(prefers-reduced-motion: reduce)')
    motion.addEventListener('change', playback)
    document.addEventListener('visibilitychange', playback)
    window.addEventListener('click', pointerClick, { passive: true })
    resizeObserver = new ResizeObserver(resize)
    resizeObserver.observe(host)
    visibilityObserver = new IntersectionObserver(([entry]) => {
      visible = entry.isIntersecting
      playback()
    })
    visibilityObserver.observe(host)
    resize()
    playback()
    return { dispose }
  } catch (error) {
    dispose()
    throw error
  }
}
