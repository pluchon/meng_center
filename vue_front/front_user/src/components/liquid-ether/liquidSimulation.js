import {
  WebGLRenderer,
  WebGLRenderTarget,
  HalfFloatType,
  LinearFilter,
  RedFormat,
  RGFormat,
  NoBlending,
  Scene,
  Camera,
  PlaneGeometry,
  Mesh,
  ShaderMaterial,
  Vector2,
  Color,
} from 'three'
import { EditorLiquid as settings } from '@/constants/liquid'
import {
  vertexShader,
  advectionShader,
  curlShader,
  vorticityShader,
  divergenceShader,
  pressureShader,
  projectionShader,
  dyeShader,
  colorShader,
} from './liquidShaders'

export function createLiquidSimulation(host) {
  const canvas = document.createElement('canvas')
  const context = canvas.getContext('webgl2', {
    alpha: true,
    antialias: false,
    premultipliedAlpha: false,
  })
  if (!context) return null
  if (!context.getExtension('EXT_color_buffer_float')) {
    context.getExtension('WEBGL_lose_context')?.loseContext()
    return null
  }
  const renderer = new WebGLRenderer({
    canvas,
    context,
    alpha: true,
    antialias: false,
    premultipliedAlpha: false,
  })
  renderer.setPixelRatio(1)
  renderer.setClearColor(0, 0)
  // Each pass overwrites its full target; clearing and sorting add no useful work.
  renderer.autoClear = false
  renderer.sortObjects = false
  const scene = new Scene(),
    camera = new Camera(),
    geometry = new PlaneGeometry(2, 2)
  const pixel = new Vector2(),
    aspect = new Vector2(),
    pointer = new Vector2(0.5, 0.5)
  const previous = pointer.clone(),
    target = pointer.clone(),
    force = new Vector2()
  const uniforms = {
    velocity: { value: null },
    pressure: { value: null },
    divergence: { value: null },
    curl: { value: null },
    dye: { value: null },
    previousDye: { value: null },
    blend: { value: 0 },
    pixel: { value: pixel },
    aspect: { value: aspect },
    pointer: { value: pointer },
    previousPointer: { value: previous },
    force: { value: force },
    dt: { value: settings.SIMULATION_FRAME_MS / 1000 },
    radius: { value: settings.BRUSH_RADIUS },
    velocityDecay: { value: settings.VELOCITY_DECAY },
    dyeDecay: { value: settings.DYE_DECAY },
    vorticity: { value: settings.VORTICITY },
    maxSpeed: { value: settings.MAX_SPEED },
  }
  const styles = getComputedStyle(host)
  const color = new Color(styles.getPropertyValue(settings.COLOR_TOKEN).trim())
  const makeMaterial = (fragmentShader, extra = {}) =>
    new ShaderMaterial({
      vertexShader,
      fragmentShader,
      uniforms: { ...uniforms, ...extra },
      depthTest: false,
      depthWrite: false,
      blending: NoBlending,
    })
  const materials = {
    advect: makeMaterial(advectionShader),
    curl: makeMaterial(curlShader),
    vortex: makeMaterial(vorticityShader),
    divergence: makeMaterial(divergenceShader),
    pressure: makeMaterial(pressureShader),
    project: makeMaterial(projectionShader),
    dye: makeMaterial(dyeShader),
  }
  const output = makeMaterial(colorShader, {
    colorBlue: { value: color },
  })
  const mesh = new Mesh(geometry, output)
  scene.add(mesh)
  let velocity,
    pressure,
    divergence,
    curl,
    dye,
    frame = 0,
    lastFrame = 0,
    lastStep = 0,
    accumulator = 0,
    elapsed = 0,
    lastInput = -Infinity,
    running = false,
    lost = false,
    bounds
  const texture = (format = RedFormat) =>
    new WebGLRenderTarget(1, 1, {
      format,
      type: HalfFloatType,
      minFilter: LinearFilter,
      magFilter: LinearFilter,
      depthBuffer: false,
      stencilBuffer: false,
    })
  velocity = [texture(RGFormat), texture(RGFormat)]
  pressure = [texture(), texture()]
  divergence = texture()
  curl = texture()
  dye = [texture(), texture()]
  const buffers = [...velocity, ...pressure, divergence, curl, ...dye]
  function resize() {
    bounds = host.getBoundingClientRect()
    const { width, height } = bounds
    if (!width || !height || lost) return
    const renderScale = Math.min(1, settings.MAX_RENDER_EDGE / Math.max(width, height))
    renderer.setSize(Math.round(width * renderScale), Math.round(height * renderScale), false)
    const edge =
      width <= settings.MOBILE_WIDTH ? settings.MOBILE_SIMULATION_EDGE : settings.SIMULATION_EDGE
    const scale = edge / Math.max(width, height),
      w = Math.max(1, Math.round(width * scale)),
      h = Math.max(1, Math.round(height * scale))
    if (velocity[0].width === w && velocity[0].height === h) return
    pixel.set(1 / w, 1 / h)
    aspect.set(width / Math.min(width, height), height / Math.min(width, height))
    for (const buffer of buffers) {
      buffer.setSize(w, h)
      renderer.setRenderTarget(buffer)
      renderer.clear()
    }
    renderer.setRenderTarget(null)
    lastFrame = 0
    lastStep = 0
    accumulator = 0
  }
  function render(material, buffer) {
    mesh.material = material
    renderer.setRenderTarget(buffer)
    renderer.render(scene, camera)
  }
  function step(dt, now) {
    elapsed += dt
    previous.copy(pointer)
    if (now - lastInput > settings.RESUME_DELAY) {
      const phase = elapsed * settings.AUTO_SPEED
      target.set(0.5 + 0.43 * Math.cos(phase), 0.5 + 0.4 * Math.sin(phase * 1.37))
    }
    pointer.lerp(target, 1 - Math.exp(-dt * settings.TAKEOVER_RATE))
    force.copy(pointer).sub(previous).multiplyScalar(settings.FORCE)
    force.clampLength(0, 0.3)
    uniforms.dt.value = dt
    uniforms.velocity.value = velocity[0].texture
    render(materials.advect, velocity[1])
    velocity.reverse()
    uniforms.velocity.value = velocity[0].texture
    render(materials.curl, curl)
    uniforms.curl.value = curl.texture
    render(materials.vortex, velocity[1])
    velocity.reverse()
    uniforms.velocity.value = velocity[0].texture
    render(materials.divergence, divergence)
    uniforms.divergence.value = divergence.texture
    for (let i = 0; i < settings.PRESSURE_ITERATIONS; i++) {
      uniforms.pressure.value = pressure[0].texture
      render(materials.pressure, pressure[1])
      pressure.reverse()
    }
    uniforms.pressure.value = pressure[0].texture
    render(materials.project, velocity[1])
    velocity.reverse()
    uniforms.velocity.value = velocity[0].texture
    uniforms.dye.value = dye[0].texture
    render(materials.dye, dye[1])
    dye.reverse()
  }
  function loop(now) {
    if (!running || lost) return
    frame = requestAnimationFrame(loop)
    const renderDelta = now - lastFrame
    if (renderDelta + settings.FRAME_TOLERANCE_MS < settings.RENDER_FRAME_MS) return
    const simulationDt = settings.SIMULATION_FRAME_MS / 1000
    const dt = lastStep ? (now - lastStep) / 1000 : simulationDt
    lastStep = now
    lastFrame =
      now -
      Math.max(
        0,
        ((renderDelta + settings.FRAME_TOLERANCE_MS) % settings.RENDER_FRAME_MS) -
          settings.FRAME_TOLERANCE_MS,
      )
    accumulator = Math.min(accumulator + dt, simulationDt * settings.MAX_SUBSTEPS)
    while (accumulator >= simulationDt) {
      step(simulationDt, now)
      accumulator -= simulationDt
    }
    uniforms.dye.value = dye[0].texture
    uniforms.previousDye.value = dye[1].texture
    uniforms.blend.value = accumulator / simulationDt
    render(output, null)
  }
  function pause() {
    running = false
    cancelAnimationFrame(frame)
    frame = 0
    lastFrame = 0
    lastStep = 0
    accumulator = 0
  }
  function start() {
    if (running || lost) return
    running = true
    frame = requestAnimationFrame(loop)
  }
  function move(event) {
    if (event.pointerType === 'touch') return
    const rect = bounds
    target.set(
      Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)),
      Math.max(0, Math.min(1, 1 - (event.clientY - rect.top) / rect.height)),
    )
    lastInput = performance.now()
  }
  function contextLost(event) {
    event.preventDefault()
    lost = true
    pause()
    canvas.style.visibility = 'hidden'
  }
  function contextRestored() {
    lost = false
    resize()
    canvas.style.visibility = ''
    if (!document.hidden) start()
  }
  canvas.addEventListener('webglcontextlost', contextLost)
  canvas.addEventListener('webglcontextrestored', contextRestored)
  window.addEventListener('pointermove', move, { passive: true })
  host.append(canvas)
  resize()
  return {
    start,
    pause,
    resize,
    dispose() {
      pause()
      window.removeEventListener('pointermove', move)
      canvas.removeEventListener('webglcontextlost', contextLost)
      canvas.removeEventListener('webglcontextrestored', contextRestored)
      buffers.forEach((buffer) => buffer.dispose())
      ;[...Object.values(materials), output].forEach((material) => material.dispose())
      geometry.dispose()
      renderer.dispose()
      renderer.forceContextLoss()
      canvas.remove()
    },
  }
}
