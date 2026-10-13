import { defineComponent, onMounted, onUnmounted, ref } from 'vue'

export default defineComponent({
  name: 'PixelBlast',
  setup() {
    const host = ref(null),
      ready = ref(false)
    let runtime,
      disposed = false
    onMounted(async () => {
      try {
        const { createPixelBlast } = await import('./pixelBlastRuntime')
        if (disposed) return
        runtime = createPixelBlast(host.value, () => {
          ready.value = false
        })
        ready.value = true
      } catch {
        runtime?.dispose()
        ready.value = false
      }
    })
    onUnmounted(() => {
      disposed = true
      runtime?.dispose()
    })
    return { host, ready }
  },
})
