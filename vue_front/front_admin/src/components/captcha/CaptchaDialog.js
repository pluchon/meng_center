import { computed, defineComponent, onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Check, Loading, RefreshRight, Right } from '@element-plus/icons-vue'
import { userApi } from '@/api/user'
import { Captcha } from '@/constants/app'
export default defineComponent({
  name: 'CaptchaDialog',
  components: { Check, Loading, RefreshRight, Right },
  setup(_, { expose }) {
    const { t } = useI18n()
    const visible = ref(false),
      loading = ref(false),
      status = ref(Captcha.IDLE),
      challenge = ref(null),
      failure = ref(''),
      progress = ref(0),
      shaking = ref(false),
      board = ref(null),
      rail = ref(null)
    let settle = null,
      drag = null,
      timer = null
    const pieceRatio = computed(() =>
      challenge.value
        ? challenge.value.templateImageWidth / challenge.value.backgroundImageWidth
        : 0,
    )
    const boardStyle = computed(() => ({
      aspectRatio: challenge.value
        ? challenge.value.backgroundImageWidth + '/' + challenge.value.backgroundImageHeight
        : Captcha.DEFAULT_RATIO,
    }))
    // 拼图左边缘能走过的比例，滑块走满时拼图正好贴到右边
    const travel = computed(() => progress.value * (1 - pieceRatio.value))
    const pieceStyle = computed(() => ({
      width: pieceRatio.value * 100 + '%',
      left: travel.value * 100 + '%',
    }))
    // 已滑过的区域盖到拼图中线，帮助对位
    const trailStyle = computed(() => ({
      width: (travel.value + pieceRatio.value / 2) * 100 + '%',
    }))
    const knobStyle = computed(() => ({
      left: `calc((100% - var(--captcha-knob)) * ${progress.value})`,
    }))
    const fillStyle = computed(() => ({
      width: `calc((100% - var(--captcha-knob)) * ${progress.value} + var(--captcha-knob) / 2)`,
    }))
    const passed = computed(() => status.value === Captcha.PASSED)
    const busy = computed(
      () => loading.value || status.value === Captcha.CHECKING || passed.value,
    )
    const message = computed(() => failure.value || (passed.value ? t('auth.captcha.passed') : ''))
    async function refresh() {
      clearTimeout(timer)
      loading.value = true
      drag = null
      try {
        challenge.value = await userApi.captcha()
        progress.value = 0
        status.value = Captcha.IDLE
      } catch (error) {
        failure.value = error.message
      } finally {
        loading.value = false
        shaking.value = false
      }
    }
    function reload() {
      failure.value = ''
      return refresh()
    }
    function solve() {
      if (settle) return Promise.resolve(null)
      challenge.value = null
      failure.value = ''
      progress.value = 0
      status.value = Captcha.IDLE
      visible.value = true
      void refresh()
      return new Promise((resolve) => {
        settle = resolve
      })
    }
    function finish(ticket) {
      clearTimeout(timer)
      if (settle) {
        settle(ticket)
        settle = null
      }
    }
    function cancel() {
      finish(null)
    }
    function startDrag(event) {
      if (busy.value || status.value === Captcha.FAILED || !challenge.value) return
      event.currentTarget.setPointerCapture(event.pointerId)
      failure.value = ''
      status.value = Captcha.DRAGGING
      drag = {
        start: event.clientX,
        startY: event.clientY,
        time: performance.now(),
        // 滑块可走的像素，和拼图可走的像素；用布局宽度，不受悬停放大的影响
        span: rail.value.clientWidth - event.currentTarget.offsetWidth,
        reach: board.value.clientWidth * (1 - pieceRatio.value),
        tracks: [{ x: 0, y: 0, t: 0, type: 'DOWN' }],
      }
    }
    function moveDrag(event) {
      if (!drag) return
      progress.value = Math.max(0, Math.min((event.clientX - drag.start) / drag.span, 1))
      drag.tracks.push({
        x: progress.value * drag.reach,
        y: event.clientY - drag.startY,
        t: performance.now() - drag.time,
        type: 'MOVE',
      })
    }
    function abortDrag() {
      drag = null
      progress.value = 0
      status.value = Captcha.IDLE
    }
    async function finishDrag(event) {
      if (!drag) return
      moveDrag(event)
      const trace = drag
      drag = null
      status.value = Captcha.CHECKING
      const duration = performance.now() - trace.time
      trace.tracks.push({ ...trace.tracks.at(-1), t: duration, type: 'UP' })
      const size = board.value.getBoundingClientRect()
      const stopTime = Date.now()
      try {
        const result = await userApi.verifyCaptcha({
          id: challenge.value.id,
          track: {
            bgImageWidth: Math.round(size.width),
            bgImageHeight: Math.round(size.height),
            startTime: stopTime - Math.round(duration),
            stopTime,
            trackList: trace.tracks,
          },
        })
        status.value = Captcha.PASSED
        timer = setTimeout(() => {
          finish(result.ticket)
          visible.value = false
        }, Captcha.SUCCESS_HOLD)
      } catch (error) {
        // 停在原位抖动并提示，稍后再换一张；每张图在服务端只能验一次
        status.value = Captcha.FAILED
        failure.value =
          error.code === 'USER_INVALID_TICKET' ? t('auth.captcha.failed') : error.message
        shaking.value = true
        timer = setTimeout(refresh, Captcha.FAILURE_HOLD)
      }
    }
    onBeforeUnmount(() => clearTimeout(timer))
    expose({ solve })
    return {
      t,
      visible,
      loading,
      status,
      challenge,
      progress,
      shaking,
      board,
      rail,
      boardStyle,
      pieceStyle,
      trailStyle,
      knobStyle,
      fillStyle,
      passed,
      busy,
      message,
      cancel,
      reload,
      startDrag,
      moveDrag,
      finishDrag,
      abortDrag,
    }
  },
})
