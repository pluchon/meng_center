import { computed, defineComponent, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useEmotionStore } from '@/stores/emotions'
export default defineComponent({
  name: 'EmotionPanel',
  emits: ['select'],
  setup() {
    const catalog = useEmotionStore(),
      active = ref('classic')
    onMounted(() => void catalog.load())
    return {
      ...useI18n(),
      active,
      catalog,
      EmotionSeries: computed(() => catalog.series),
      current: computed(() => ({
        items: catalog.series.find((series) => series.key === active.value)?.items || [],
      })),
    }
  },
})
