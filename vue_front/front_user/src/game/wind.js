// 风：给标准材质插一段顶点代码，越高的顶点摆得越多；同一个实例整体按自己的位置取相位，花头和花茎一起摆不会脱开
import { MaterialPluginBase } from '@babylonjs/core/Materials/materialPluginBase'

// 所有摆动共用一个时钟，世界每帧更新
export const windClock = { time: 0 }

class WindPlugin extends MaterialPluginBase {
  constructor(material, amount) {
    super(material, 'Wind', 200, { WIND: false })
    this.amount = amount
    this._enable(true)
  }

  prepareDefines(defines) {
    defines.WIND = true
  }

  getClassName() {
    return 'WindPlugin'
  }

  getUniforms() {
    return {
      ubo: [
        { name: 'windTime', size: 1, type: 'float' },
        { name: 'windAmount', size: 1, type: 'float' },
      ],
      vertex: `#ifdef WIND
        uniform float windTime;
        uniform float windAmount;
      #endif`,
    }
  }

  bindForSubMesh(ubo) {
    ubo.updateFloat('windTime', windClock.time)
    ubo.updateFloat('windAmount', this.amount)
  }

  getCustomCode(shaderType) {
    if (shaderType !== 'vertex') return null
    return {
      CUSTOM_VERTEX_UPDATE_WORLDPOS: `
        #ifdef WIND
          float windPhase = windTime * 1.7 + finalWorld[3].x * 0.23 + finalWorld[3].z * 0.19;
          float windBend = max(positionUpdated.y, 0.0) * windAmount;
          worldPos.x += (sin(windPhase) + 0.35 * sin(windPhase * 2.3)) * windBend;
          worldPos.z += cos(windPhase * 0.8) * windBend * 0.6;
        #endif
      `,
    }
  }
}

// amount 是每单位高度摆多远
export function addWind(material, amount) {
  return new WindPlugin(material, amount)
}
