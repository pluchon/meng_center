import { computed, defineComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouteName } from '@/router/names'
import { StateArt, StateSize, failureArt } from '@/constants/stateArt'
import { stateLoadingSprite } from '@/assets/images'
export default defineComponent({
  name: 'PageState',
  props: {
    state: { type: String, required: true },
    // StateArt 里的名字；不传则按状态用通用插画
    art: { type: String, default: '' },
    loadingSpriteUrl: { type: String, default: '' },
    size: { type: String, default: StateSize.MEDIUM },
    // 覆盖默认说明文字
    text: { type: String, default: '' },
    // 请求失败的错误对象，用来按类型挑插画
    error: { type: [Object, Boolean], default: null },
  },
  emits: ['retry'],
  setup(props) {
    const { t } = useI18n()
    const image = computed(() => {
      if (props.art) return StateArt[props.art]
      if (props.state === 'error') return failureArt(props.error)
      if (props.state === 'unauthenticated') return StateArt.unauthorized
      return StateArt.blockEmpty
    })
    const line = computed(() => props.text || t('common.' + props.state))
    return { t, RouteName, image, line, loadingSprite: computed(() => props.loadingSpriteUrl || stateLoadingSprite) }
  },
})
