import { computed, defineComponent, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { IconPlayerPause } from '@tabler/icons-vue'
import { gameApi } from '@/api/game'
import { AUTOSAVE_MS, GamePhase, Prompt, SpotKind } from '@/constants/game'
import { RouteName } from '@/router/names'
import {
  gameBarkTexture,
  gameChestTexture,
  gameCoinTexture,
  gameEndingArt,
  gameFaceTexture,
  gameGrassTexture,
  gameLeafTexture,
  gamePathTexture,
  gameRockTexture,
  gameRoofTexture,
  gameSandTexture,
  gameStarTexture,
  gameTitleArt,
  gameWallTexture,
} from '@/assets/images'
import DialogShell from '@/components/dialog-shell/DialogShell.vue'
import GameMap from '@/components/game-map/GameMap.vue'
import GameShop from '@/components/game-shop/GameShop.vue'
import GameStamina from '@/components/game-stamina/GameStamina.vue'
import PageState from '@/components/page-state/PageState.vue'

const TEXTURES = Object.freeze({
  grass: gameGrassTexture,
  sand: gameSandTexture,
  rock: gameRockTexture,
  path: gamePathTexture,
  leaf: gameLeafTexture,
  bark: gameBarkTexture,
  wall: gameWallTexture,
  roof: gameRoofTexture,
  face: gameFaceTexture,
  coin: gameCoinTexture,
  chest: gameChestTexture,
  star: gameStarTexture,
})
const TOAST_MS = 2200
const CONTROL_KEYS = ['move', 'run', 'jump', 'glide', 'climb', 'look', 'interact', 'map', 'pause']
const QUALITIES = ['high', 'medium', 'low']
// 画质和音量只是这台浏览器的偏好，存在本地
const Saved = Object.freeze({ QUALITY: 'meng-island-quality', VOLUME: 'meng-island-volume' })

function readLocal(key, fallback) {
  try {
    return localStorage.getItem(key) ?? fallback
  } catch {
    return fallback
  }
}

function writeLocal(key, value) {
  try {
    localStorage.setItem(key, value)
  } catch {
    // 无痕模式等情况下存不了，下次用默认值
  }
}

// 萌岛页面：标题页、加载、HUD、商店、暂停和结局是 Vue，3D 世界和声音在 src/game 里按需加载
export default defineComponent({
  name: 'GameView',
  components: { DialogShell, GameMap, GameShop, GameStamina, PageState },
  setup() {
    const { t } = useI18n()
    const canvas = ref(null)
    const phase = ref(GamePhase.TITLE)
    const error = ref(null)
    const noWebgl = ref(false)
    const profile = reactive({ coins: 0, stars: 0, cleared: false, hat: null, outfit: null })
    const items = ref([])
    const starTotal = ref(3)
    const prompt = ref(null)
    const toasts = ref([])
    const shopOpen = ref(false)
    const paused = ref(false)
    const ending = ref(null)
    // 鼠标是否已锁定在画面上；没锁时提示点一下画面
    const locked = ref(false)
    const mapOpen = ref(false)
    const shrine = ref({ x: 0, z: 0 })
    const quality = ref(QUALITIES.includes(readLocal(Saved.QUALITY, '')) ? readLocal(Saved.QUALITY, '') : 'medium')
    const volume = ref(Math.min(100, Math.max(0, Number(readLocal(Saved.VOLUME, 70)) || 0)))
    // 世界对象、声音和存档计时都不放进响应式
    let world = null
    let audioContext = null
    let audio = null
    let timer = 0
    let saved = null
    let toastId = 0

    function toast(text) {
      const id = ++toastId
      toasts.value = [...toasts.value.slice(-2), { id, text }]
      setTimeout(() => {
        toasts.value = toasts.value.filter((x) => x.id !== id)
      }, TOAST_MS)
    }

    const missing = computed(() => (profile.cleared ? 0 : Math.max(0, starTotal.value - profile.stars)))
    const promptText = computed(() => {
      if (prompt.value === Prompt.SHRINE && missing.value) return t('game.starsMissing', { count: missing.value })
      return prompt.value ? t('game.prompt.' + prompt.value) : ''
    })
    const errorText = computed(() => t(noWebgl.value ? 'game.noWebgl' : 'game.failed'))

    function save(leaving) {
      if (!world) return
      const p = world.position()
      const moved = !saved || Math.hypot(p.x - saved.x, p.z - saved.z) > 0.5 || Math.abs(p.y - saved.y) > 0.5
      if (!moved) return
      saved = p
      if (leaving) gameApi.saveOnLeave(p)
      else gameApi.save(p).catch(() => {})
    }
    const leave = () => save(true)

    async function pickup(spot, x, z) {
      try {
        const result = await gameApi.pickup({ spotId: spot.id, x, z })
        profile.coins = result.coins
        if (result.taken) return true
        if (spot.kind === SpotKind.STAR) {
          profile.stars = result.stars
          world?.setStars(result.stars)
          toast(t('game.gotStar', { count: result.stars }))
        } else toast(t('game.gotCoins', { count: result.amount }))
        return true
      } catch (e) {
        if (!e.notified) toast(e.message)
        return false
      }
    }

    function hold() {
      world?.setPaused(true)
    }

    async function lightShrine() {
      if (missing.value) return toast(t('game.starsMissing', { count: missing.value }))
      try {
        const result = await gameApi.shrine()
        profile.coins = result.coins
        profile.cleared = true
        world?.lightShrine()
        hold()
        ending.value = { reward: result.amount }
      } catch (e) {
        if (!e.notified) toast(e.message)
      }
    }

    const hooks = {
      pickup,
      prompt: (next) => {
        prompt.value = next
      },
      interact: (kind) => {
        if (kind === Prompt.SHOP) {
          hold()
          shopOpen.value = true
        } else if (kind === Prompt.SHRINE) lightShrine()
      },
      pause,
      look: (value) => {
        locked.value = value
      },
      notice: (key) => toast(t('game.notice.' + key)),
    }

    // 地图打开时角色停下；再按 M、Esc 或点地图关上
    function toggleMap() {
      if (mapOpen.value) return closeOverlay()
      hold()
      mapOpen.value = true
    }
    function mapKeys(event) {
      if (event.code === 'KeyM' && !event.repeat && !paused.value && !shopOpen.value && !ending.value) toggleMap()
      else if (event.code === 'Escape' && mapOpen.value) closeOverlay()
    }
    const locate = () => (world ? { ...world.position(), heading: world.heading() } : null)
    const readStamina = () => (world && !paused.value && !mapOpen.value ? world.stamina() : null)

    function pause() {
      hold()
      paused.value = true
    }

    function closeOverlay() {
      shopOpen.value = false
      paused.value = false
      ending.value = null
      mapOpen.value = false
      world?.resume()
    }

    async function enter() {
      // 声音要在点击的当下创建，浏览器才允许出声
      if (!audioContext && window.AudioContext) audioContext = new AudioContext()
      audioContext?.resume()
      phase.value = GamePhase.LOADING
      error.value = null
      noWebgl.value = false
      paused.value = false
      try {
        const [me, map] = await Promise.all([gameApi.profile({ silent: true }), gameApi.world({ silent: true })])
        Object.assign(profile, {
          coins: me.coins,
          stars: me.stars,
          cleared: me.cleared,
          hat: me.hat ?? null,
          outfit: me.outfit ?? null,
        })
        items.value = map.items
        starTotal.value = map.spots.filter((s) => s.kind === SpotKind.STAR).length
        shrine.value = map.shrine
        await nextTick()
        const [{ createIsland }, { createAudio }, { QUALITY }] = await Promise.all([
          import('@/game/world'),
          import('@/game/audio'),
          import('@/game/post'),
        ])
        if (audioContext && !audio) {
          audio = createAudio(audioContext)
          audio.setVolume(volume.value / 100)
        }
        world?.dispose()
        world = null
        try {
          world = await createIsland(canvas.value, { profile: me, world: map, urls: TEXTURES, quality: QUALITY[quality.value], audio }, hooks)
        } catch (e) {
          noWebgl.value = true
          throw e
        }
        saved = world.position()
        phase.value = GamePhase.PLAYING
        clearInterval(timer)
        timer = setInterval(() => save(false), AUTOSAVE_MS)
        window.addEventListener('pagehide', leave)
        window.addEventListener('keydown', mapKeys)
        await nextTick()
        world.focus()
      } catch (e) {
        world?.dispose()
        world = null
        error.value = e
        phase.value = GamePhase.ERROR
      }
    }

    // 换画质要按新档位重建世界，先存一下当前位置
    function changeQuality(next) {
      if (next === quality.value) return
      quality.value = next
      writeLocal(Saved.QUALITY, next)
      save(false)
      enter()
    }

    watch(volume, (v) => {
      audio?.setVolume(v / 100)
      writeLocal(Saved.VOLUME, String(v))
    })

    function bought({ id, coins }) {
      profile.coins = coins
      items.value = items.value.map((i) => (i.id === id ? { ...i, owned: true } : i))
    }

    function equipped({ hat, outfit }) {
      profile.hat = hat
      profile.outfit = outfit
      world?.setLook(hat, outfit)
    }

    onBeforeUnmount(() => {
      save(true)
      clearInterval(timer)
      window.removeEventListener('pagehide', leave)
      window.removeEventListener('keydown', mapKeys)
      world?.dispose()
      world = null
      audio?.dispose()
    })

    return {
      t,
      canvas,
      phase,
      error,
      errorText,
      profile,
      items,
      prompt,
      promptText,
      missing,
      toasts,
      shopOpen,
      paused,
      ending,
      locked,
      mapOpen,
      shrine,
      quality,
      volume,
      locate,
      readStamina,
      toggleMap,
      enter,
      pause,
      closeOverlay,
      changeQuality,
      bought,
      equipped,
      GamePhase,
      Prompt,
      RouteName,
      IconPlayerPause,
      controlKeys: CONTROL_KEYS,
      qualities: QUALITIES,
      titleArt: gameTitleArt,
      endingArt: gameEndingArt,
      coinArt: gameCoinTexture,
      starArt: gameStarTexture,
    }
  },
})
