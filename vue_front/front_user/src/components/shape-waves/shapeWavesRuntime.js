import { createRippleField } from './rippleField'
import { BLUR_SHADER, COMPOSITE_SHADER, INTRO_END, SCENE_SHADER } from './shapeWavesShaders'

const cellSize = 10
const introDuration = 1.6
const glowStrength = 0.35

function themeColor(style, name) {
  const source = style.getPropertyValue(name).trim().slice(1)
  const hex = source.length === 3 ? source.replace(/./g, (digit) => digit + digit) : source
  return [0, 2, 4].map((offset) => parseInt(hex.slice(offset, offset + 2), 16) / 255)
}

// 使用原版 WebGPU 四阶段渲染；只保留创作中心实际用到的混合图形背景。
export function createShapeWaves(root, canvas, setReady) {
  let disposed = false
  let failed = false
  let gpu
  let resizeObserver
  let unsubscribeError
  let chargeBuffer
  let frameId = 0
  let lastFrame = 0
  let elapsed = 0
  let introTime = 0
  let dpr = 1
  let cellPx = cellSize
  let originY = 0
  let cols = 1
  let rows = 1
  let field = createRippleField(cols, rows)
  let bounds
  let render
  const pointer = { x: 0, y: 0, at: 0, inside: false }
  const motion = matchMedia('(prefers-reduced-motion: reduce)')

  function stop() {
    cancelAnimationFrame(frameId)
    frameId = 0
    lastFrame = 0
  }

  function wake() {
    if (disposed || failed || document.hidden || !render) return
    if (!frameId) frameId = requestAnimationFrame(render)
  }

  function visibility() {
    if (document.hidden) stop()
    else wake()
  }

  function motionChanged() {
    field.reset()
    chargeBuffer?.write(field.charges)
    wake()
  }

  function invalidateBounds() {
    bounds = null
  }

  function move(event) {
    if (motion.matches || document.hidden || failed || disposed) return
    bounds ??= root.getBoundingClientRect()
    const now = performance.now()
    const x = event.clientX - bounds.left
    const y = event.clientY - bounds.top
    const inside = x >= 0 && y >= 0 && x <= bounds.width && y <= bounds.height
    if (inside) {
      const duration = pointer.inside ? Math.max(8, now - pointer.at) : 16
      const distance = pointer.inside ? Math.hypot(x - pointer.x, y - pointer.y) : 0
      field.splash(
        (x * dpr) / cellPx - 0.5,
        (y * dpr - originY) / cellPx - 0.5,
        (40 * dpr) / cellPx,
        Math.min(1, 0.22 + (distance / duration) * 0.6) * 0.4,
      )
      wake()
    }
    Object.assign(pointer, { x, y, at: now, inside })
  }

  function release() {
    stop()
    resizeObserver?.disconnect()
    document.removeEventListener('visibilitychange', visibility)
    motion.removeEventListener('change', motionChanged)
    window.removeEventListener('pointermove', move)
    window.removeEventListener('scroll', invalidateBounds, true)
    unsubscribeError?.()
    chargeBuffer?.destroy()
    chargeBuffer = undefined
    gpu?.dispose()
    gpu = undefined
  }

  function fail() {
    if (disposed || failed) return
    failed = true
    release()
    setReady(false)
  }

  async function start() {
    if (!navigator.gpu) return
    try {
      const { effect, frame, init, sampler, storage, surface, target, uniforms } =
        await import('vgpu')
      if (disposed) return
      const activeGpu = await init({ powerPreference: 'low-power' })
      if (disposed) {
        activeGpu.dispose()
        return
      }
      gpu = activeGpu
      unsubscribeError = gpu.onError(fail)
      const measure = () => {
        dpr = Math.min(window.devicePixelRatio || 1, 2)
        return [
          Math.max(1, Math.round(canvas.clientWidth * dpr)),
          Math.max(1, Math.round(canvas.clientHeight * dpr)),
        ]
      }
      const size = measure()
      const format = navigator.gpu.getPreferredCanvasFormat()
      const output = surface(gpu, canvas, { dpr, size, autoResize: false, format })
      const style = getComputedStyle(root)
      const params = uniforms(gpu, {
        resolution: [size[0], size[1], 1 / size[0], 1 / size[1]],
        placement: [0, 0, 1, INTRO_END],
        grid: [10, 0.75, 0, 1],
        field: [320, 0.54, 2.8, 0],
        motion: [0, 0, 0, 0.25],
        color: [...themeColor(style, '--meng-shape-color'), 1],
        hover: [...themeColor(style, '--meng-shape-hover'), 1],
        background: [...themeColor(style, '--meng-shape-background'), 1],
      })
      const linearSampler = sampler(gpu, {
        minFilter: 'linear',
        magFilter: 'linear',
        addressModeU: 'clamp-to-edge',
        addressModeV: 'clamp-to-edge',
      })
      chargeBuffer = storage(gpu, 4, 'read')
      chargeBuffer.write(field.charges)
      const scene = effect(gpu, SCENE_SHADER, {
        label: 'shape-waves-scene',
        set: { params, charges: chargeBuffer },
      })
      const halfSize = ([width, height]) => [Math.ceil(width / 2), Math.ceil(height / 2)]
      const sceneTarget = target(gpu, { size, format: 'rgba8unorm', label: 'shape-waves-scene' })
      const glowA = target(gpu, {
        size: halfSize(size),
        format: 'rgba8unorm',
        label: 'shape-waves-glow-a',
      })
      const glowB = target(gpu, {
        size: halfSize(size),
        format: 'rgba8unorm',
        label: 'shape-waves-glow-b',
      })
      const blurParamsX = uniforms(gpu, { direction: [0, 0, 4, 1] })
      const blurParamsY = uniforms(gpu, { direction: [0, 0, 4, 0] })
      const blurX = effect(gpu, BLUR_SHADER, {
        label: 'shape-waves-glow-x',
        set: { blur: blurParamsX, sourceTexture: sceneTarget, sourceSampler: linearSampler },
      })
      const blurY = effect(gpu, BLUR_SHADER, {
        label: 'shape-waves-glow-y',
        set: { blur: blurParamsY, sourceTexture: glowA, sourceSampler: linearSampler },
      })
      const compositeParams = uniforms(gpu, { strength: [2 * glowStrength, 0, 0, 0] })
      const composite = effect(gpu, COMPOSITE_SHADER, {
        label: 'shape-waves-composite',
        set: {
          composite: compositeParams,
          sceneTexture: sceneTarget,
          glowTexture: glowB,
          linearSampler,
        },
      })
      await Promise.all([
        scene.compile(sceneTarget),
        blurX.compile(glowA),
        blurY.compile(glowB),
        composite.compile({ colors: [format] }),
      ])
      if (disposed || failed) return

      function resize() {
        if (disposed || failed) return
        invalidateBounds()
        const next = measure()
        if (next[0] !== output.size[0] || next[1] !== output.size[1]) {
          output.resize(next)
          sceneTarget.resize(next)
          glowA.resize(halfSize(next))
          glowB.resize(halfSize(next))
        }
        const [width, height] = output.size
        const nextCols = Math.max(1, Math.round(width / (cellSize * dpr)))
        cellPx = width / nextCols
        const nextRows = Math.max(1, Math.floor(height / cellPx))
        originY = (height - nextRows * cellPx) / 2
        if (nextCols !== cols || nextRows !== rows) {
          cols = nextCols
          rows = nextRows
          field = createRippleField(cols, rows)
          const nextBuffer = storage(gpu, field.charges.byteLength, 'read')
          nextBuffer.write(field.charges)
          scene.set({ charges: nextBuffer })
          chargeBuffer.destroy()
          chargeBuffer = nextBuffer
        }
        const half = halfSize(next)
        blurParamsX.set({ direction: [1 / half[0], 0, 4, 1] })
        blurParamsY.set({ direction: [0, 1 / half[1], 4, 0] })
        params.set({
          resolution: [width, height, 1 / width, 1 / height],
          grid: [cellPx, 0.75, 0, cols],
        })
        wake()
      }

      render = (now) => {
        frameId = 0
        if (disposed || failed || document.hidden) return
        const delta = lastFrame ? Math.min(0.1, (now - lastFrame) / 1000) : 0
        lastFrame = now
        if (!motion.matches) {
          elapsed += delta * 0.1
          introTime += delta
        }
        try {
          if (field.advance(delta)) chargeBuffer.write(field.charges)
          params.set({
            placement: [
              0,
              originY,
              rows,
              motion.matches
                ? INTRO_END
                : Math.min(INTRO_END, (introTime / introDuration) * INTRO_END),
            ],
            field: [32 * cellPx, 0.54, 2.8, elapsed],
          })
          frame(gpu, (current) => {
            current.pass(sceneTarget, scene)
            current.pass(glowA, blurX)
            current.pass(glowB, blurY)
            current.pass(output, composite)
          })
          setReady(true)
          if (!motion.matches) frameId = requestAnimationFrame(render)
          else lastFrame = 0
        } catch {
          fail()
        }
      }
      resizeObserver = new ResizeObserver(() => {
        try {
          resize()
        } catch {
          fail()
        }
      })
      resizeObserver.observe(root)
      document.addEventListener('visibilitychange', visibility)
      motion.addEventListener('change', motionChanged)
      window.addEventListener('pointermove', move, { passive: true })
      window.addEventListener('scroll', invalidateBounds, { capture: true, passive: true })
      resize()
    } catch {
      fail()
    }
  }
  void start()
  return () => {
    disposed = true
    release()
  }
}
