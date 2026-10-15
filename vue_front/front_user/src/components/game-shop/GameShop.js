import { computed, defineComponent, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { IconBuildingStore } from '@tabler/icons-vue'
import { gameApi } from '@/api/game'
import { ItemKind, OUTFIT_COLORS } from '@/constants/game'
import { gameCoinTexture, gameShopPortrait } from '@/assets/images'
import DialogShell from '@/components/dialog-shell/DialogShell.vue'

// 团子的小铺：买帽子和围巾、换装；金币和装备由页面持有，这里只发请求再把结果交回去
export default defineComponent({
  name: 'GameShop',
  components: { DialogShell },
  props: {
    items: { type: Array, required: true },
    coins: { type: Number, required: true },
    hat: { type: String, default: null },
    outfit: { type: String, default: null },
  },
  emits: ['close', 'bought', 'equipped'],
  setup(props, { emit }) {
    const { t } = useI18n()
    const busy = ref('')
    const groups = computed(() => [
      { kind: ItemKind.HAT, title: 'game.shop.hats', items: props.items.filter((i) => i.kind === ItemKind.HAT) },
      { kind: ItemKind.OUTFIT, title: 'game.shop.outfits', items: props.items.filter((i) => i.kind === ItemKind.OUTFIT) },
    ])
    const worn = (item) => (item.kind === ItemKind.HAT ? props.hat : props.outfit) === item.id
    const swatchStyle = (item) =>
      item.kind === ItemKind.OUTFIT ? { background: OUTFIT_COLORS[item.id] } : null

    async function buy(item) {
      busy.value = item.id
      try {
        const result = await gameApi.purchase(item.id)
        emit('bought', { id: item.id, coins: result.coins })
        ElMessage.success(t('game.shop.bought'))
        await equip(item.kind === ItemKind.HAT ? item.id : props.hat, item.kind === ItemKind.OUTFIT ? item.id : props.outfit)
      } catch (error) {
        if (!error.notified) ElMessage.error(error.message)
      } finally {
        busy.value = ''
      }
    }

    async function equip(hat, outfit) {
      const profile = await gameApi.equip(hat, outfit)
      emit('equipped', { hat: profile.hat ?? null, outfit: profile.outfit ?? null })
    }

    async function toggle(item) {
      busy.value = item.id
      const on = !worn(item)
      try {
        if (item.kind === ItemKind.HAT) await equip(on ? item.id : null, props.outfit)
        else await equip(props.hat, on ? item.id : null)
      } catch (error) {
        if (!error.notified) ElMessage.error(error.message)
      } finally {
        busy.value = ''
      }
    }

    return {
      t,
      busy,
      groups,
      worn,
      swatchStyle,
      buy,
      toggle,
      ItemKind,
      IconBuildingStore,
      portrait: gameShopPortrait,
      coinArt: gameCoinTexture,
    }
  },
})
