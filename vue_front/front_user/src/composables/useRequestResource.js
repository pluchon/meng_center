import { onUnmounted, reactive } from 'vue'

// 独立加载与重试，切换参数或离页时取消旧请求并丢弃过期响应。
export function useRequestResource(fetch, initial) {
  const state = reactive({ data: initial, loading: false, error: null })
  let controller,
    generation = 0,
    disposed = false
  function clear() {
    generation++
    controller?.abort()
    Object.assign(state, { data: initial, loading: false, error: null })
  }
  async function load() {
    if (disposed) return false
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
    disposed = true
    generation++
    controller?.abort()
  })
  return { state, load, clear }
}
