import { defineComponent, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import {
  naitangHappy,
  naitangIdleBreath,
  naitangIdleDoze,
  naitangIdleLook,
  naitangIdleStill,
  naitangIdleStretch,
  naitangIdleTail,
  naitangIdleWash,
  naitangOffline,
  naitangTalk,
  naitangThink,
} from '@/assets/images'

const idle = [
  { src: naitangIdleBreath, duration: 3000, weight: 40 },
  { src: naitangIdleTail, duration: 2500, weight: 15 },
  { src: naitangIdleStretch, duration: 3000, weight: 10 },
  { src: naitangIdleWash, duration: 3000, weight: 15 },
  { src: naitangIdleLook, duration: 3000, weight: 10 },
  { src: naitangIdleDoze, duration: 4000, weight: 10 },
]
const states = {
  think: { src: naitangThink },
  talk: { src: naitangTalk },
  offline: { src: naitangOffline },
}
const happy = { src: naitangHappy, duration: 1200 }
const still = { src: naitangIdleStill }

function nextIdle() {
  let chance = Math.random() * 100
  for (const clip of idle) {
    chance -= clip.weight
    if (chance < 0) return clip
  }
  return idle[0]
}

export default defineComponent({
  name: 'NaitangPet',
  props: {
    state: { type: String, default: 'idle' },
    cheers: { type: Number, default: 0 },
  },
  setup(props) {
    const preference = window.matchMedia('(prefers-reduced-motion: reduce)')
    const reduced = ref(preference.matches)
    const clip = shallowRef(still)
    const sequence = ref(0)
    let timer

    function play(next) {
      clearTimeout(timer)
      clip.value = next
      sequence.value++
    }
    function schedule(event) {
      if (Number(event.currentTarget.dataset.sequence) !== sequence.value) return
      const current = clip.value
      if (current.duration) {
        clearTimeout(timer)
        timer = setTimeout(() => play(current === happy ? idle[0] : nextIdle()), current.duration)
      }
    }
    function fallback(event) {
      if (Number(event.currentTarget.dataset.sequence) === sequence.value && clip.value !== still)
        play(still)
    }
    function changeMotion(event) {
      reduced.value = event.matches
    }

    watch(
      [() => props.state, () => props.cheers, reduced],
      ([state, cheers, reduce], [, previousCheers]) => {
        const celebrate = previousCheers !== undefined && cheers !== previousCheers
        play(reduce ? still : states[state] || (celebrate ? happy : idle[0]))
      },
      { immediate: true },
    )
    onMounted(() => preference.addEventListener('change', changeMotion))
    onUnmounted(() => {
      preference.removeEventListener('change', changeMotion)
      clearTimeout(timer)
    })
    return { clip, sequence, schedule, fallback }
  },
})
