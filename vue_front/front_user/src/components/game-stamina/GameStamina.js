import { computed, defineComponent, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

const CIRCUMFERENCE = 2 * Math.PI * 17
// 耐力满了以后再停留一会儿才消失
const HIDE_AFTER = 1.2

// 耐力环：跟在角色身边，用掉耐力时出现，透支时变红，满了一会儿后隐藏
export default defineComponent({
  name: 'GameStamina',
  props: {
    // 返回 { value, max, exhausted, x, y }，x、y 是角色在画面上的位置（0 到 1）
    read: { type: Function, required: true },
  },
  setup(props) {
    const { t } = useI18n()
    const value = ref(100)
    const max = ref(100)
    const exhausted = ref(false)
    const left = ref(50)
    const top = ref(50)
    const visible = ref(false)
    let fullSince = 0
    let frame = 0

    function tick(now) {
      frame = requestAnimationFrame(tick)
      const s = props.read()
      if (!s) return
      value.value = s.value
      max.value = s.max
      exhausted.value = s.exhausted
      left.value = Math.min(96, Math.max(4, s.x * 100 + 6))
      top.value = Math.min(92, Math.max(8, s.y * 100 - 4))
      if (s.value < s.max) fullSince = now
      visible.value = (now - fullSince) / 1000 < HIDE_AFTER
    }

    onMounted(() => {
      frame = requestAnimationFrame(tick)
    })
    onBeforeUnmount(() => cancelAnimationFrame(frame))

    const dash = computed(() => `${(value.value / max.value) * CIRCUMFERENCE} ${CIRCUMFERENCE}`)
    return { t, value, max, exhausted, left, top, visible, dash }
  },
})
