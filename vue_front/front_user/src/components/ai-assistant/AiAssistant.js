import { computed, defineComponent, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { aiApi } from '@/api/ai'
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
  components: { GlowRing, PageState },
  props: {
    open: Boolean,
    mode: { type: String, required: true },
    draft: { type: Object, default: null },
  },
  emits: ['update:open', 'insert'],
  setup(props, { emit }) {
    const days = ref(7),
      revision = ref(0)
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
    watch(
      () => [props.open, days.value],
      ([open]) => {
        if (open) void load()
        else resource.clear()
      },
      { immediate: true },
    )
    return {
      ...useI18n(),
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
