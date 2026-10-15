// 小特效：捡到东西时冒星星，跑步和落地时扬起一点尘土
import { ParticleSystem } from '@babylonjs/core/Particles/particleSystem'
import '@babylonjs/core/Particles/particleSystemComponent'
import { RawTexture } from '@babylonjs/core/Materials/Textures/rawTexture'
import { Texture } from '@babylonjs/core/Materials/Textures/texture'
import { Color4 } from '@babylonjs/core/Maths/math.color'
import { Vector3 } from '@babylonjs/core/Maths/math.vector'

const Burst = Object.freeze({
  COIN: { count: 18, color: new Color4(1, 0.88, 0.45, 1), size: [0.18, 0.4], power: [1.5, 3] },
  STAR: { count: 40, color: new Color4(1, 0.96, 0.7, 1), size: [0.25, 0.6], power: [2, 4.5] },
  CHEST: { count: 30, color: new Color4(1, 0.9, 0.55, 1), size: [0.2, 0.5], power: [2.5, 4] },
})

// 一张中间实、四周渐隐的小圆点，尘土用
function softDot(scene) {
  const n = 32
  const data = new Uint8Array(n * n * 4)
  for (let y = 0; y < n; y++)
    for (let x = 0; x < n; x++) {
      const d = Math.hypot(x - n / 2 + 0.5, y - n / 2 + 0.5) / (n / 2)
      const i = (y * n + x) * 4
      data.set([255, 255, 255, Math.round(Math.max(0, 1 - d) ** 1.6 * 255)], i)
    }
  return RawTexture.CreateRGBATexture(data, n, n, scene, false)
}

export function createEffects(scene, starUrl) {
  const star = new Texture(starUrl, scene)
  const dot = softDot(scene)

  // 一次性的星星：发完就自己销毁
  function sparkle(x, y, z, kind) {
    const look = Burst[kind] ?? Burst.COIN
    const ps = new ParticleSystem('sparkle', look.count, scene)
    ps.particleTexture = star
    ps.emitter = new Vector3(x, y, z)
    ps.minEmitBox = new Vector3(-0.2, 0, -0.2)
    ps.maxEmitBox = new Vector3(0.2, 0.3, 0.2)
    ps.color1 = look.color
    ps.color2 = new Color4(1, 1, 1, 1)
    ps.colorDead = new Color4(1, 1, 1, 0)
    ps.minSize = look.size[0]
    ps.maxSize = look.size[1]
    ps.minLifeTime = 0.45
    ps.maxLifeTime = 0.9
    ps.minEmitPower = look.power[0]
    ps.maxEmitPower = look.power[1]
    ps.direction1 = new Vector3(-1, 2.2, -1)
    ps.direction2 = new Vector3(1, 3, 1)
    ps.gravity = new Vector3(0, -7, 0)
    ps.minAngularSpeed = -4
    ps.maxAngularSpeed = 4
    ps.blendMode = ParticleSystem.BLENDMODE_ADD
    ps.emitRate = 0
    ps.manualEmitCount = look.count
    ps.targetStopDuration = 1.2
    ps.disposeOnStop = true
    ps.start()
  }

  // 脚下的尘土：一直挂着，跑起来才出
  const dust = new ParticleSystem('dust', 80, scene)
  dust.particleTexture = dot
  dust.emitter = new Vector3(0, -100, 0)
  dust.minEmitBox = new Vector3(-0.3, 0, -0.3)
  dust.maxEmitBox = new Vector3(0.3, 0.05, 0.3)
  dust.color1 = new Color4(0.96, 0.92, 0.84, 0.55)
  dust.color2 = new Color4(0.92, 0.95, 0.88, 0.45)
  dust.colorDead = new Color4(1, 1, 1, 0)
  dust.minSize = 0.25
  dust.maxSize = 0.6
  dust.minLifeTime = 0.35
  dust.maxLifeTime = 0.7
  dust.minEmitPower = 0.3
  dust.maxEmitPower = 0.9
  dust.direction1 = new Vector3(-0.6, 0.8, -0.6)
  dust.direction2 = new Vector3(0.6, 1.4, 0.6)
  dust.gravity = new Vector3(0, -0.6, 0)
  dust.blendMode = ParticleSystem.BLENDMODE_STANDARD
  dust.emitRate = 0
  dust.start()

  function feet(x, y, z, running) {
    dust.emitter.set(x, y + 0.05, z)
    dust.emitRate = running ? 22 : 0
  }

  // 落地时一圈尘土
  function land(x, y, z) {
    dust.emitter.set(x, y + 0.05, z)
    dust.manualEmitCount = 14
  }

  return { sparkle, feet, land }
}
