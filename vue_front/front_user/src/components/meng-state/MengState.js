import { computed, defineComponent } from 'vue'
import { useRouter } from 'vue-router'
import { RouteName } from '@/router/names'
import { useI18n } from 'vue-i18n'
import { companionLoadingSprite } from '@/assets/images'
import { CompanionError } from '@/constants/meng'
import PageState from '@/components/page-state/PageState.vue'
// 每种异常对应的插画和文案键（meng.*）；art 为数组时依次是整页、区块
const Modes = Object.freeze({
  login: { art: 'mengLogin', label: 'login' },
  character: { art: 'mengCharacter', label: 'characterMissing' },
  topic: { art: 'mengTopic', label: 'topicMissing' },
  resting: { art: ['mengResting', 'mengRestingChat'], label: 'resting' },
  restricted: { art: 'mengRestricted', label: 'restricted' },
  maintenance: { art: 'mengMaintenance', label: 'maintenance' },
  network: { art: 'mengNetwork', label: 'network' },
  error: { art: ['mengPageFailure', 'mengFailure'], label: 'retry' },
})
const Empties = Object.freeze({
  works: { art: 'mengEmptyWorks', label: 'noWorks' },
  memory: { art: 'mengEmptyMemory', label: 'noMemory' },
  topic: { art: 'mengNewTopic', label: 'newTopicHint' },
})
// 按请求层整理好的错误挑异常类型：先看业务码，再看 HTTP 状态
function failureMode(error) {
  if (error?.code === CompanionError.UNAVAILABLE) return 'resting'
  if (error?.code === 'MAINTENANCE') return 'maintenance'
  if (error?.code === 'COMPANION_SESSION_NOT_FOUND') return 'topic'
  return { 0: 'network', 401: 'login', 403: 'restricted', 404: 'topic' }[error?.status] || 'error'
}
export default defineComponent({
  name: 'MengState',
  components: { PageState },
  props: {
    loading: Boolean,
    error: { type: Object, default: null },
    kind: { type: String, default: '' },
    page: Boolean,
    compact: Boolean,
    empty: Boolean,
  },
  emits: ['retry'],
  setup(props, { emit }) {
    const { t } = useI18n(),
      router = useRouter()
    const mode = computed(() => props.kind || failureMode(props.error))
    const entry = computed(() =>
      props.empty ? Empties[props.kind] || Empties.topic : Modes[mode.value] || Modes.error,
    )
    const art = computed(() => {
      const value = entry.value.art
      return Array.isArray(value) ? value[props.page ? 0 : 1] : value
    })
    const label = computed(() => t('meng.' + (props.loading ? 'loading' : entry.value.label)))
    return {
      art,
      label,
      sprite: companionLoadingSprite,
      retry: () =>
        mode.value === 'restricted' ? router.push({ name: RouteName.HOME }) : emit('retry'),
    }
  },
})
