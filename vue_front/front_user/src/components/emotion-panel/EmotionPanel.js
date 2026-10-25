import { computed, defineComponent, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useEmotionStore } from '@/stores/emotions'
export default defineComponent({
  name: 'EmotionPanel',
  emits: ['select'],
  setup() {
    const catalog = useEmotionStore(),
      active = ref('classic'),
      query = ref('')
    onMounted(() => void catalog.load())
    return {
      ...useI18n(),
      active,
      catalog,
      query,
      EmotionSeries: computed(() => catalog.series),
      current: computed(() => ({
        items: (catalog.series.find((series) => series.key === active.value)?.items || []).filter(
          (item) =>
            [item.name, item.description, item.meaning, ...(item.tags || [])]
              .join(' ')
              .includes(query.value.trim()),
        ),
      })),
    }
  },
})
