import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import request from '@/api/request'
import { emotionImages } from '@/constants/comments'

export const useEmotionStore = defineStore('emotions', () => {
  const items = ref([]),
    loading = ref(false),
    error = ref(null)
  let pending = null,
    expires = 0
  async function load(force = false) {
    if (pending) return pending
    if (!force && Date.now() < expires) return
    loading.value = true
    pending = request
      .get('/content/emotions', { silent: true })
      .then((catalog) => {
        items.value = catalog.items.map((item) => ({
          ...item,
          image: emotionImages[item.imageKey],
        }))
        expires = Date.now() + 600000
        error.value = null
      })
      .catch((failure) => {
        error.value = failure
      })
      .finally(() => {
        loading.value = false
        pending = null
      })
    return pending
  }
  const series = computed(() =>
    ['classic', 'cirno'].map((key) => ({
      key,
      items: items.value.filter((item) => item.series === key && item.enabled !== false),
    })),
  )
  return { items, loading, error, series, load }
})
