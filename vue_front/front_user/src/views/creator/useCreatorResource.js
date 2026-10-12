import { onUnmounted, reactive } from 'vue'

// 每块数据独立请求、取消和重试，切换时丢弃过期响应。
export function useCreatorResource(fetch, initial) {
  const state = reactive({ data: initial, loading: false, error: null })
  let controller,
    generation = 0
  async function load() {
    controller?.abort()
    controller = new AbortController()
    const current = ++generation
    state.loading = true
    state.error = null
    try {
      const result = await fetch(controller.signal)
      if (current === generation) {
        state.data = result
        return true
      }
    } catch (error) {
      if (current === generation && !error.cancelled) state.error = error
    } finally {
      if (current === generation) state.loading = false
    }
    return false
  }
  onUnmounted(() => {
    generation++
    controller?.abort()
  })
  return { state, load }
}
