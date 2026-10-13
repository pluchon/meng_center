import { defineComponent, onMounted, onUnmounted, ref } from 'vue'
import { createShapeWaves } from './shapeWavesRuntime'

export default defineComponent({
  name: 'ShapeWaves',
  setup() {
    const host = ref(null)
    const canvas = ref(null)
    const ready = ref(false)
    let release = () => {}
    onMounted(() => {
      release = createShapeWaves(host.value, canvas.value, (value) => {
        ready.value = value
      })
    })
    onUnmounted(() => release())
    return { host, canvas, ready }
  },
})
