// 风：给标准材质插一段顶点代码，越高的顶点摆得越多；同一个实例整体按自己的位置取相位，花头和花茎一起摆不会脱开。
// 角色附近的草被拨开；只在角色周围一圈生成的花草，在圈的边缘慢慢缩回地里，看不出边界
import { MaterialPluginBase } from '@babylonjs/core/Materials/materialPluginBase'

// 所有摆动共用：时钟、角色位置、花草圈的中心和半径，世界每帧更新
export const windClock = { time: 0, x: 0, y: 0, z: 0, cx: 0, cz: 0, radius: 60 }

class WindPlugin extends MaterialPluginBase {
  constructor(material, amount, ring) {
    super(material, 'Wind', 200, { WIND: false, WIND_RING: false })
    this.amount = amount
    this.ring = ring
    this._enable(true)
  }

  prepareDefines(defines) {
    defines.WIND = true
    defines.WIND_RING = this.ring
  }

  getClassName() {
    return 'WindPlugin'
  }

  getUniforms() {
    return {
      ubo: [
        { name: 'windTime', size: 1, type: 'float' },
        { name: 'windAmount', size: 1, type: 'float' },
        { name: 'windPusher', size: 3, type: 'vec3' },
        { name: 'windRing', size: 3, type: 'vec3' },
      ],
      vertex: `#ifdef WIND
        uniform float windTime;
        uniform float windAmount;
        uniform vec3 windPusher;
        uniform vec3 windRing;
      #endif`,
    }
  }

  bindForSubMesh(ubo) {
    ubo.updateFloat('windTime', windClock.time)
    ubo.updateFloat('windAmount', this.amount)
    ubo.updateFloat3('windPusher', windClock.x, windClock.y, windClock.z)
    ubo.updateFloat3('windRing', windClock.cx, windClock.cz, windClock.radius)
  }

  getCustomCode(shaderType) {
    if (shaderType !== 'vertex') return null
    return {
      CUSTOM_VERTEX_UPDATE_WORLDPOS: `
        #ifdef WIND
          vec3 windBase = finalWorld[3].xyz;
          float windPhase = windTime * 1.7 + windBase.x * 0.23 + windBase.z * 0.19;
          float windHeight = max(positionUpdated.y, 0.0);
          float windBend = windHeight * windAmount;
          worldPos.x += (sin(windPhase) + 0.35 * sin(windPhase * 2.3)) * windBend;
          worldPos.z += cos(windPhase * 0.8) * windBend * 0.6;
          #ifdef WIND_RING
            vec2 windAway = windBase.xz - windPusher.xz;
            float windNear = (1.0 - smoothstep(0.3, 1.6, length(windAway))) * step(abs(windBase.y - windPusher.y), 2.0);
            worldPos.xz += normalize(windAway + 0.0001) * windNear * windHeight * 0.9;
            worldPos.y -= windNear * windHeight * 0.35;
            float windFade = 1.0 - smoothstep(windRing.z - 14.0, windRing.z - 2.0, length(windBase.xz - windRing.xy));
            worldPos.y = mix(windBase.y, worldPos.y, windFade);
          #endif
        #endif
      `,
    }
  }
}

// amount 是每单位高度摆多远；ring 表示这是只在角色周围生成的花草
export function addWind(material, amount, ring = false) {
  return new WindPlugin(material, amount, ring)
}
