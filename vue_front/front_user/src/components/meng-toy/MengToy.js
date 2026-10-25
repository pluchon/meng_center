import { computed, defineComponent, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
const policy =
  "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:"
export default defineComponent({
  name: 'MengToy',
  props: { body: { type: String, required: true }, title: { type: String, required: true } },
  setup(props) {
    const running = ref(false),
      revision = ref(0),
      frame = ref(null)
    const document = computed(() => {
      if (new TextEncoder().encode(props.body).length > 20000) return ''
      return (
        '<!doctype html><html><head><meta http-equiv="Content-Security-Policy" content="' +
        policy +
        '"></head><body>' +
        props.body +
        '</body></html>'
      )
    })
    watch(
      () => props.body,
      () => {
        running.value = false
        revision.value++
      },
    )
    return {
      ...useI18n(),
      running,
      revision,
      frame,
      document,
      run: async () => {
        running.value = true
        revision.value++
        await nextTick()
        frame.value?.scrollIntoView({ block: 'nearest' })
      },
      fullscreen: () => {
        void frame.value?.requestFullscreen?.().catch(() => {})
      },
    }
  },
})
