import { Color, Mesh, Program, Renderer, Triangle, Vec2 } from 'ogl'
import { vertex, fragment } from './darkVeilShaders'

const frameInterval = 1000 / 30
const paletteTokens = ['--meng-bg', '--meng-account-veil-violet', '--meng-account-veil-blue']

export function createDarkVeil(host, onFailure) {
  let renderer, geometry, program, mesh, resizeObserver, visibilityObserver, paletteObserver, motion
  let frame = 0,
    disposed = false,
    visible = true,
    previous = null,
    elapsed = 0,
    initialized = false,
    sizedWidth = 0,
    sizedHeight = 0,
    paletteKey = ''
  function draw() {
    if (disposed || !initialized || !renderer || renderer.gl.isContextLost()) return
    program.uniforms.uTime.value = elapsed * 0.5
    renderer.render({ scene: mesh })
  }
  function animate(time) {
    frame = 0
    if (disposed || document.hidden || !visible || motion.matches) return
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
    draw()
    if (!motion.matches) {
      previous = performance.now()
      frame = requestAnimationFrame(animate)
    }
  }
  function resize() {
    if (disposed) return
    const { width, height } = host.getBoundingClientRect()
    if (!width || !height) return
    if (width === sizedWidth && height === sizedHeight) return
    sizedWidth = width
    sizedHeight = height
    const edge = width <= 640 ? 480 : 896
    renderer.dpr = Math.min(1, edge / Math.max(width, height))
    renderer.setSize(width, height)
    program.uniforms.uResolution.value.set(
      renderer.gl.drawingBufferWidth,
      renderer.gl.drawingBufferHeight,
    )
    if (!document.hidden && visible) draw()
  }
  function palette() {
    if (disposed) return
    const css = getComputedStyle(document.documentElement)
    const values = paletteTokens.map((token) => css.getPropertyValue(token).trim())
    const strength = Number(css.getPropertyValue('--meng-account-veil-strength'))
    const key = values.join(':') + ':' + strength
    if (paletteKey === key) return
    paletteKey = key
    const colors = values.map((value) => new Color(value))
    program.uniforms.uBackdrop.value = colors[0]
    program.uniforms.uViolet.value = colors[1]
    program.uniforms.uBlue.value = colors[2]
    program.uniforms.uStrength.value = strength
    if (!document.hidden && visible) draw()
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
    paletteObserver?.disconnect()
    motion?.removeEventListener('change', playback)
    document.removeEventListener('visibilitychange', playback)
    renderer?.gl.canvas.removeEventListener('webglcontextlost', contextLost)
    geometry?.remove()
    program?.remove()
    renderer?.gl.getExtension('WEBGL_lose_context')?.loseContext()
    renderer?.gl.canvas.remove()
  }
  try {
    const canvas = document.createElement('canvas')
    const attributes = {
      alpha: false,
      antialias: false,
      depth: false,
      stencil: false,
      premultipliedAlpha: false,
      powerPreference: 'low-power',
    }
    const context =
      canvas.getContext('webgl2', attributes) || canvas.getContext('webgl', attributes)
    if (!context) throw new Error('WebGL unavailable')
    renderer = new Renderer({ canvas, ...attributes, dpr: 1 })
    const gl = renderer.gl
    geometry = new Triangle(gl)
    program = new Program(gl, {
      vertex,
      fragment,
      depthTest: false,
      depthWrite: false,
      cullFace: false,
      uniforms: {
        uTime: { value: 0 },
        uResolution: { value: new Vec2(1, 1) },
        uBackdrop: { value: new Color() },
        uViolet: { value: new Color() },
        uBlue: { value: new Color() },
        uStrength: { value: 0 },
      },
    })
    if (!gl.getProgramParameter(program.program, gl.LINK_STATUS))
      throw new Error('DarkVeil shader unavailable')
    mesh = new Mesh(gl, { geometry, program })
    host.appendChild(canvas)
    canvas.addEventListener('webglcontextlost', contextLost)
    motion = matchMedia('(prefers-reduced-motion: reduce)')
    motion.addEventListener('change', playback)
    document.addEventListener('visibilitychange', playback)
    resizeObserver = new ResizeObserver(resize)
    resizeObserver.observe(host)
    visibilityObserver = new IntersectionObserver(([entry]) => {
      if (visible === entry.isIntersecting) return
      visible = entry.isIntersecting
      playback()
    })
    visibilityObserver.observe(host)
    paletteObserver = new MutationObserver(palette)
    paletteObserver.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['class', 'data-theme'],
    })
    resize()
    palette()
    initialized = true
    playback()
    return { dispose }
  } catch (error) {
    dispose()
    throw error
  }
}
