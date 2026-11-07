import { computed, defineComponent, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { IconRefresh } from '@tabler/icons-vue'
import { aiApi } from '@/api/ai'
import { notifyFailure } from '@/composables/useFailureNotice'
import { useRequestResource } from '@/composables/useRequestResource'
import {
  editorMagicIcon,
  messageCloseIcon,
  growthTrendIcon,
  growthCalendarIcon,
  growthFlameIcon,
  creatorPostsIcon,
  creatorViewsIcon,
  creatorFavoritesIcon,
} from '@/assets/images'
import GlowRing from '@/components/glow-ring/GlowRing.vue'
import PageState from '@/components/page-state/PageState.vue'
export default defineComponent({
  name: 'AiAssistant',
  components: { GlowRing, PageState, IconRefresh },
  props: {
    open: Boolean,
    mode: { type: String, required: true },
    draft: { type: Object, default: null },
  },
  emits: ['update:open', 'insert'],
  setup(props, { emit }) {
    const days = ref(7),
      revision = ref(0),
      refreshing = ref(false)
    const resource = useRequestResource(
      (signal) =>
        props.mode === 'inspirations'
          ? aiApi.inspirations({ ...props.draft }, { signal, silent: true })
          : aiApi[props.mode](days.value, { signal, silent: true }),
      null,
    )
    async function load() {
      if (await resource.load()) revision.value++
    }
    // 切换天数要重新生成：先清掉上一段的结果，让等待时的流光占位重新出来
    watch(
      () => [props.open, days.value],
      ([open], previous) => {
        if (open && previous && previous[1] !== days.value) resource.clear()
        if (open) void load()
        else resource.clear()
      },
      { immediate: true },
    )
    // 总结按点展示：模型按行分点，旧的缓存是一整段，就按句末标点切开
    const points = computed(() =>
      (resource.state.data?.result?.summary || '')
        .split(/\n|(?<=[。！？!?；;])/u)
        .map((point) => point.trim())
        .filter(Boolean),
    )
    const refreshesLeft = computed(() => resource.state.data?.suggestionRefreshes ?? 0)
    // 只换「可以试试看」的建议，次数按这次复盘算，用完按钮变灰
    async function refreshSuggestions() {
      if (refreshing.value || !refreshesLeft.value) return
      refreshing.value = true
      const period = days.value
      try {
        const result = await aiApi.creatorSuggestions(period)
        if (period === days.value && resource.state.data) resource.state.data = result
      } catch (error) {
        if (!error.cancelled) notifyFailure(error)
      } finally {
        refreshing.value = false
      }
    }
    return {
      ...useI18n(),
      points,
      refreshesLeft,
      refreshing,
      refreshSuggestions,
      editorMagicIcon,
      messageCloseIcon,
      days,
      revision,
      resource: resource.state,
      load,
      insert(text) {
        emit('insert', text)
        emit('update:open', false)
      },
      metrics: computed(() => {
        const stats = resource.state.data?.stats
        if (!stats) return []
        return props.mode === 'growth'
          ? [
              {
                label: 'experience',
                icon: growthTrendIcon,
                value: stats.dailyExperience.reduce((sum, day) => sum + day.amount, 0),
              },
              { label: 'checkIns', icon: growthCalendarIcon, value: stats.checkInDays },
              { label: 'streak', icon: growthFlameIcon, value: stats.longestStreak },
            ]
          : [
              { label: 'posts', icon: creatorPostsIcon, value: stats.postCount },
              {
                label: 'views',
                icon: creatorViewsIcon,
                value: stats.dailyMetrics.reduce((sum, day) => sum + day.viewCount, 0),
              },
              {
                label: 'favorites',
                icon: creatorFavoritesIcon,
                value: stats.dailyMetrics.reduce((sum, day) => sum + day.favoriteCount, 0),
              },
            ]
      }),
    }
  },
})
