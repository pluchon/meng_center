// 卡通渲染和地表混合：给标准材质插着色器片段，阴影、雾、实例这些标准材质自带的能力照常可用
import { MaterialPluginBase } from '@babylonjs/core/Materials/materialPluginBase'
import { StandardMaterial } from '@babylonjs/core/Materials/standardMaterial'
import { Color3 } from '@babylonjs/core/Maths/math.color'
import { SNOW_LINE, WORLD } from './terrain'

// 所有卡通材质共用的光照总量：明暗分层按它的比例算，昼夜变化时每帧更新
export const toonState = { light: 1.2 }

// 明暗分成三层：背光、受光、高光；阈值是相对整体光量的比例
class ToonPlugin extends MaterialPluginBase {
  constructor(material) {
    super(material, 'Toon', 300, { TOON: false })
    this._enable(true)
  }

  prepareDefines(defines) {
    defines.TOON = true
  }

  getClassName() {
    return 'ToonPlugin'
  }

  getUniforms() {
    return {
      ubo: [{ name: 'toonLight', size: 1, type: 'float' }],
      fragment: `#ifdef TOON
        uniform float toonLight;
      #endif`,
    }
  }

  bindForSubMesh(ubo) {
    ubo.updateFloat('toonLight', toonState.light)
  }

  getCustomCode(shaderType) {
    if (shaderType !== 'fragment') return null
    return {
      CUSTOM_FRAGMENT_BEFORE_FOG: `
        #ifdef TOON
        {
          float toonL = dot(diffuseBase, vec3(0.3333)) / max(toonLight, 0.01);
          vec3 toonHue = diffuseBase / max(dot(diffuseBase, vec3(0.3333)), 0.0001);
          float toonRamp = mix(0.66, 0.95, smoothstep(0.6, 0.66, toonL)) + 0.07 * smoothstep(0.9, 0.95, toonL);
          // 白天的总光量约 1.25，按它归一，正午受光面就是原色，不会发白
          vec3 toonBase = mix(vec3(1.0), toonHue, 0.55) * toonRamp * clamp(toonLight / 1.25, 0.3, 1.0);
          vec3 toonDiffuse = clamp(toonBase * diffuseColor + emissiveColor + vAmbientColor, 0.0, 1.0) * baseColor.rgb;
          color.rgb = toonDiffuse * baseAmbientColor + finalSpecular;
        }
        #endif
      `,
    }
  }
}

// 地表：草地打底，按混合图叠沙滩、小路、森林和花海的色调；陡坡用三向投影贴岩石，高处缓坡积雪
class TerrainPlugin extends MaterialPluginBase {
  constructor(material, textures) {
    super(material, 'TerrainSplat', 250, { TERRAINSPLAT: false })
    this.textures = textures
    this._enable(true)
  }

  prepareDefines(defines) {
    defines.TERRAINSPLAT = true
  }

  getClassName() {
    return 'TerrainPlugin'
  }

  isReadyForSubMesh() {
    return Object.values(this.textures).every((t) => t.isReady())
  }

  getSamplers(samplers) {
    samplers.push('splatSampler', 'grassSampler', 'sandSampler', 'rockSampler', 'pathSampler')
  }

  getUniforms() {
    return {
      ubo: [
        { name: 'worldHalf', size: 1, type: 'float' },
        { name: 'snowLine', size: 1, type: 'float' },
      ],
      fragment: `#ifdef TERRAINSPLAT
        uniform float worldHalf;
        uniform float snowLine;
      #endif`,
    }
  }

  bindForSubMesh(ubo) {
    ubo.updateFloat('worldHalf', WORLD.HALF)
    ubo.updateFloat('snowLine', SNOW_LINE)
    ubo.setTexture('splatSampler', this.textures.splat)
    ubo.setTexture('grassSampler', this.textures.grass)
    ubo.setTexture('sandSampler', this.textures.sand)
    ubo.setTexture('rockSampler', this.textures.rock)
    ubo.setTexture('pathSampler', this.textures.path)
  }

  getCustomCode(shaderType) {
    if (shaderType !== 'fragment') return null
    return {
      CUSTOM_FRAGMENT_DEFINITIONS: `
        #ifdef TERRAINSPLAT
          uniform sampler2D splatSampler;
          uniform sampler2D grassSampler;
          uniform sampler2D sandSampler;
          uniform sampler2D rockSampler;
          uniform sampler2D pathSampler;
        #endif
      `,
      CUSTOM_FRAGMENT_UPDATE_DIFFUSE: `
        #ifdef TERRAINSPLAT
        {
          vec4 sp = texture2D(splatSampler, (vPositionW.xz + worldHalf) / (2.0 * worldHalf));
          vec2 tuv = vPositionW.xz * 0.14;
          vec3 grass = mix(texture2D(grassSampler, tuv).rgb, texture2D(grassSampler, tuv * 0.31 + 0.37).rgb, 0.4);
          grass = mix(grass, grass * vec3(0.74, 0.84, 0.7), sp.b);
          grass = mix(grass, grass * vec3(1.06, 1.02, 0.84) + vec3(0.06, 0.04, 0.0), sp.a * 0.7);
          vec3 sand = texture2D(sandSampler, tuv * 0.8).rgb;
          vec3 col = mix(grass, sand, sp.r);
          col = mix(col, texture2D(pathSampler, tuv * 0.7).rgb, sp.g);
          vec3 n = normalize(vNormalW);
          vec3 bw = pow(abs(n), vec3(4.0));
          bw /= bw.x + bw.y + bw.z;
          vec3 rock = texture2D(rockSampler, vPositionW.zy * 0.07).rgb * bw.x
            + texture2D(rockSampler, vPositionW.xz * 0.07).rgb * bw.y
            + texture2D(rockSampler, vPositionW.xy * 0.07).rgb * bw.z;
          col = mix(col, rock * vec3(1.02, 1.0, 1.04), smoothstep(0.8, 0.66, n.y));
          float snow = smoothstep(snowLine - 10.0, snowLine + 10.0, vPositionW.y + rock.r * 12.0) * smoothstep(0.5, 0.7, n.y);
          col = mix(col, vec3(0.95, 0.97, 1.0), snow);
          col = mix(col, sand * vec3(0.82, 0.9, 0.95), smoothstep(0.6, -1.2, vPositionW.y) * (1.0 - snow));
          baseColor.rgb = col;
        }
        #endif
      `,
    }
  }
}

export function addToon(material) {
  return new ToonPlugin(material)
}

export function addTerrain(material, textures) {
  return new TerrainPlugin(material, textures)
}

// 卡通材质：纯色或贴图，几乎没有高光
export function toonMaterial(scene, name, hex, texture) {
  const m = new StandardMaterial(name, scene)
  m.diffuseColor = Color3.FromHexString(hex)
  m.specularColor = new Color3(0.03, 0.03, 0.03)
  if (texture) m.diffuseTexture = texture
  addToon(m)
  return m
}
