import { computed, defineComponent, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { EmotionSeries } from '@/constants/comments'
export default defineComponent({
  name: 'EmotionPanel',
  emits: ['select'],
  setup() {
    const active = ref(EmotionSeries[0].key)
    return {
      ...useI18n(),
      active,
      EmotionSeries,
      current: computed(() => EmotionSeries.find((series) => series.key === active.value)),
    }
  },
})
