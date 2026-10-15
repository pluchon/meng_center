import { computed, defineComponent, nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { IconPlayerPause } from '@tabler/icons-vue'
import { gameApi } from '@/api/game'
import { AUTOSAVE_MS, GamePhase, Prompt, SpotKind } from '@/constants/game'
import { RouteName } from '@/router/names'
import {
  gameBarkTexture,
  gameChestTexture,
  gameCloudsTexture,
  gameCoinTexture,
  gameEndingArt,
  gameFaceTexture,
  gameGrassTexture,
  gameLeafTexture,
  gamePathTexture,
  gameRockTexture,
  gameRoofTexture,
  gameSandTexture,
  gameSkyTexture,
  gameStarTexture,
  gameTitleArt,
  gameWallTexture,
  gameWaterTexture,
} from '@/assets/images'
import DialogShell from '@/components/dialog-shell/DialogShell.vue'
import GameMap from '@/components/game-map/GameMap.vue'
import GameShop from '@/components/game-shop/GameShop.vue'
import PageState from '@/components/page-state/PageState.vue'

const TEXTURES = Object.freeze({
  grass: gameGrassTexture,
  sand: gameSandTexture,
  rock: gameRockTexture,
  path: gamePathTexture,
  water: gameWaterTexture,
  leaf: gameLeafTexture,
  bark: gameBarkTexture,
  wall: gameWallTexture,
  roof: gameRoofTexture,
  sky: gameSkyTexture,
  face: gameFaceTexture,
  coin: gameCoinTexture,
  chest: gameChestTexture,
  star: gameStarTexture,
  clouds: gameCloudsTexture,
})
const TOAST_MS = 2200
const CONTROL_KEYS = ['move', 'run', 'jump', 'look', 'interact', 'map', 'pause']

// 萌岛页面：标题页、加载、HUD、商店、暂停和结局是 Vue，3D 世界在 src/game 里按需加载
export default defineComponent({
  name: 'GameView',
  components: { DialogShell, GameMap, GameShop, PageState },
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
    // 世界对象不放进响应式
    let world = null
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
      phase.value = GamePhase.LOADING
      error.value = null
      noWebgl.value = false
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
        const { createIsland } = await import('@/game/world')
        world?.dispose()
        try {
          world = await createIsland(canvas.value, { profile: me, world: map, urls: TEXTURES }, hooks)
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
      locate,
      toggleMap,
      enter,
      pause,
      closeOverlay,
      bought,
      equipped,
      GamePhase,
      Prompt,
      RouteName,
      IconPlayerPause,
      controlKeys: CONTROL_KEYS,
      titleArt: gameTitleArt,
      endingArt: gameEndingArt,
      coinArt: gameCoinTexture,
      starArt: gameStarTexture,
    }
  },
})
