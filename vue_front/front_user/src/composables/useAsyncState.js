import { ref } from 'vue'
export function useAsyncState(action, initial = null) {
  const loading = ref(false),
    error = ref(null),
    data = ref(initial)
  async function execute(...args) {
    loading.value = true
    error.value = null
    try {
      data.value = await action(...args)
      return data.value
    } catch (failure) {
      error.value = failure
      return null
    } finally {
      loading.value = false
    }
  }
  return { loading, error, data, execute }
}
