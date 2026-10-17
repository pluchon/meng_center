import { computed, defineComponent } from 'vue'
import { useRouter } from 'vue-router'
import { RouteName } from '@/router/names'
import { useI18n } from 'vue-i18n'
import { companionLoadingSprite } from '@/assets/images'
import PageState from '@/components/page-state/PageState.vue'
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
    const mode = computed(
      () =>
        props.kind ||
        (props.error?.status === 401
          ? 'login'
          : props.error?.code === 'AI_UNAVAILABLE'
            ? 'resting'
            : props.error?.status === 403
              ? 'restricted'
              : props.error?.code === 'COMPANION_SESSION_NOT_FOUND' || props.error?.status === 404
                ? 'topic'
                : props.error?.code === 'MAINTENANCE'
                  ? 'maintenance'
                  : props.error?.status === 0
                    ? 'network'
                    : 'error'),
    )
    const art = computed(() =>
      props.empty
        ? { works: 'mengEmptyWorks', memory: 'mengEmptyMemory' }[props.kind] || 'mengNewTopic'
        : {
            login: 'mengLogin',
            character: 'mengCharacter',
            topic: 'mengTopic',
            resting: props.page ? 'mengResting' : 'mengRestingChat',
            network: 'mengNetwork',
            restricted: 'mengRestricted',
            maintenance: 'mengMaintenance',
          }[mode.value] || (props.page ? 'mengPageFailure' : 'mengFailure'),
    )
    const label = computed(() =>
      t(
        'meng.' +
          (props.loading
            ? 'loading'
            : props.empty
              ? { works: 'noWorks', memory: 'noMemory' }[props.kind] || 'newTopicHint'
              : {
                  login: 'login',
                  character: 'characterMissing',
                  topic: 'topicMissing',
                  network: 'network',
                  resting: 'resting',
                  restricted: 'restricted',
                  maintenance: 'maintenance',
                }[mode.value] || 'retry'),
      ),
    )
    return {
      art,
      label,
      sprite: companionLoadingSprite,
      retry: () =>
        mode.value === 'restricted' ? router.push({ name: RouteName.HOME }) : emit('retry'),
    }
  },
})
