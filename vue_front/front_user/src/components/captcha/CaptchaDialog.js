import { defineComponent, ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Right } from '@element-plus/icons-vue'
import { userApi } from '@/api/user'
import PageState from '@/components/page-state/PageState.vue'
export default defineComponent({
  name: 'CaptchaDialog',
  components: { Right, PageState },
  setup(_, { expose }) {
    const { t } = useI18n()
    const visible = ref(false),
      loading = ref(false),
      checking = ref(false),
      challenge = ref(null),
      failure = ref(''),
      position = ref(0),
      board = ref(null),
      rail = ref(null)
    let settle = null,
      drag = null
    const pieceWidth = computed(() =>
      challenge.value
        ? (challenge.value.templateImageWidth / challenge.value.backgroundImageWidth) * 100
        : 0,
    )
    const knobStyle = computed(() => {
      const progress = position.value / Math.max(1, 100 - pieceWidth.value)
      return { left: `calc(${progress * 100}% - var(--meng-control-height) * ${progress})` }
    })
    async function refresh() {
      loading.value = true
      failure.value = ''
      position.value = 0
      drag = null
      try {
        challenge.value = await userApi.captcha()
      } catch (error) {
        failure.value = error.message
      } finally {
        loading.value = false
      }
    }
    function solve() {
      if (settle) return Promise.resolve(null)
      visible.value = true
      void refresh()
      return new Promise((resolve) => {
        settle = resolve
      })
    }
    function cancel() {
      if (settle) {
        settle(null)
        settle = null
      }
    }
    function startDrag(event) {
      if (checking.value || !challenge.value) return
      event.currentTarget.setPointerCapture(event.pointerId)
      drag = {
        start: event.clientX,
        startY: event.clientY,
        time: performance.now(),
        tracks: [{ x: 0, y: 0, t: 0, type: 'down' }],
      }
    }
    function moveDrag(event) {
      if (!drag) return
      const width = board.value.getBoundingClientRect().width
      const distance = Math.max(
        0,
        Math.min(event.clientX - drag.start, width * (1 - pieceWidth.value / 100)),
      )
      position.value = (distance / width) * 100
      drag.tracks.push({
        x: distance,
        y: event.clientY - drag.startY,
        t: performance.now() - drag.time,
        type: 'move',
      })
    }
    function abortDrag() {
      drag = null
      position.value = 0
    }
    async function finishDrag(event) {
      if (!drag) return
      moveDrag(event)
      const trace = drag
      drag = null
      checking.value = true
      trace.tracks.push({ ...trace.tracks.at(-1), t: performance.now() - trace.time, type: 'up' })
      try {
        const result = await userApi.verifyCaptcha({
          id: challenge.value.id,
          track: {
            bgImageWidth: board.value.getBoundingClientRect().width,
            bgImageHeight: board.value.getBoundingClientRect().height,
            startTime: Date.now() - Math.round(performance.now() - trace.time),
            endTime: Date.now(),
            trackList: trace.tracks,
          },
        })
        if (settle) {
          settle(result.ticket)
          settle = null
        }
        visible.value = false
      } catch (error) {
        await refresh()
        failure.value = error.message || t('auth.captcha.failed')
      } finally {
        checking.value = false
      }
    }
    expose({ solve })
    return {
      t,
      visible,
      loading,
      checking,
      challenge,
      failure,
      position,
      board,
      rail,
      pieceWidth,
      knobStyle,
      cancel,
      refresh,
      startDrag,
      moveDrag,
      finishDrag,
      abortDrag,
    }
  },
})
