import { computed, defineComponent, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  companionPetBody,
  companionPetEyesClosed,
  companionPetEyesHalf,
  companionPetMouth1,
  companionPetMouth2,
  companionPetMouth3,
  companionPetMouth4,
  companionPetMouth5,
  companionPetMouth6,
  companionPetPoseCrouch,
  companionPetPoseLeap,
  companionPetPoseSmile,
  companionPetTailStrip,
} from '@/assets/images'
import { Companion } from '@/constants/companion'

// 平时待机；说话和开心都有明确的结束时机，不会一直循环
export default defineComponent({
  name: 'NaitangPet',
  props: {
    talking: { type: Boolean, default: false },
    cheers: { type: Number, default: 0 },
  },
  setup(props) {
    const cheerRequested = ref(false),
      syllable = ref(0)
    const cheering = computed(() => cheerRequested.value && !props.talking)
    const stillMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches
    let cheerTimer,
      idleTimer,
      syllableTimer

    function cheer() {
      if (stillMotion() || cheerRequested.value) return
      cheerRequested.value = true
      cheerTimer = setTimeout(() => {
        cheerRequested.value = false
      }, Companion.PET_CHEER_MS)
    }
    function stopTalking() {
      clearInterval(syllableTimer)
      syllable.value = 0
    }
    // 只是看起来在说话，和语音内容不同步
    function startTalking() {
      stopTalking()
      if (stillMotion()) return
      let index = 0
      syllable.value = Companion.PET_SYLLABLES[0]
      syllableTimer = setInterval(() => {
        index = (index + 1) % Companion.PET_SYLLABLES.length
        syllable.value = Companion.PET_SYLLABLES[index]
      }, Companion.PET_SYLLABLE_MS)
    }
    watch(
      () => props.talking,
      (talking) => (talking ? startTalking() : stopTalking()),
      { immediate: true },
    )
    watch(() => props.cheers, cheer)
    onMounted(() => {
      idleTimer = setInterval(() => {
        if (!props.talking && !document.hidden && Math.random() < Companion.PET_IDLE_CHEER_CHANCE)
          cheer()
      }, Companion.PET_IDLE_CHECK_MS)
    })
    onUnmounted(() => {
      clearTimeout(cheerTimer)
      clearInterval(idleTimer)
      stopTalking()
    })
    return {
      cheering,
      syllable,
      mouths: [
        companionPetMouth1,
        companionPetMouth2,
        companionPetMouth3,
        companionPetMouth4,
        companionPetMouth5,
        companionPetMouth6,
      ],
      companionPetBody,
      companionPetEyesClosed,
      companionPetEyesHalf,
      companionPetPoseCrouch,
      companionPetPoseLeap,
      companionPetPoseSmile,
      companionPetTailStrip,
    }
  },
})
