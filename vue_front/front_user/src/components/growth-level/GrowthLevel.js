import { computed, defineComponent, nextTick, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { userAvatar, communitySparkle } from '@/assets/images'
import { GrowthTones } from '@/constants/growth'
import { useCountUp } from '@/composables/useCountUp'
import PageState from '@/components/page-state/PageState.vue'
export default defineComponent({
  name: 'GrowthLevel',
  components: { PageState },
  props: {
    account: { type: Object, required: true },
    levels: { type: Array, required: true },
    name: { type: String, required: true },
    avatar: { type: String, default: '' },
    roadLoading: Boolean,
    roadError: { type: Object, default: null },
  },
  emits: ['retry'],
  setup(props) {
    const { t, tm, rt } = useI18n()
    const road = ref(null),
      revealed = ref(false)
    // 经验数字滚到当前值；进度条和等级之路在进场后才从 0 长出来
    const shown = useCountUp(() => ({ exp: props.account.exp }), { duration: 1100 })
    onMounted(() => requestAnimationFrame(() => requestAnimationFrame(() => (revealed.value = true))))
    watch(
      [() => props.account.level, () => props.levels.length],
      async () => {
        await nextTick()
        const host = road.value,
          current = host?.querySelector('.current')
        if (current && host.scrollWidth > host.clientWidth)
          host.scrollLeft = Math.max(
            0,
            current.offsetLeft + current.offsetWidth / 2 - host.clientWidth / 2,
          )
      },
      { immediate: true },
    )
    function levelName(level) {
      const names = tm('growth.levels')
      return names[level - 1] ? rt(names[level - 1]) : t('growth.level', { level })
    }
    const progress = computed(() =>
      props.account.nextLevelExp == null
        ? 100
        : Math.max(
            0,
            Math.min(
              100,
              (100 * (props.account.exp - props.account.levelStartExp)) /
                (props.account.nextLevelExp - props.account.levelStartExp),
            ),
          ),
    )
    const path = computed(() =>
      props.levels
        .map((_, index) => {
          const x = index * 100 + 50,
            y = index % 2 ? 35 : 17
          return index ? `C${x - 50},${index % 2 ? 17 : 35} ${x - 50},${y} ${x},${y}` : `M${x},${y}`
        })
        .join(' '),
    )
    const width = computed(() => Math.max(1, props.levels.length) * 100)
    const shownProgress = computed(() => (revealed.value ? progress.value : 0))
    const roadProgress = computed(() =>
      props.account.nextLevelExp == null
        ? 100
        : Math.min(
            100,
            (100 * (props.account.level - 1 + progress.value / 100)) /
              Math.max(1, props.levels.length - 1),
          ),
    )
    return {
      t,
      road,
      revealed,
      userAvatar,
      communitySparkle,
      levelName,
      progress,
      shown,
      shownProgress,
      path,
      width,
      roadProgress,
      GrowthTones,
    }
  },
})
