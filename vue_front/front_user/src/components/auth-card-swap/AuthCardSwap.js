import { defineComponent, ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { RouteName } from '@/router/names'
import { CardSwapMotion } from '@/constants/visual'
import ParticleBackground from '@/components/particle-background/ParticleBackground.vue'

const authRoutes = new Set([RouteName.LOGIN, RouteName.REGISTER])
export default defineComponent({
  name: 'AuthCardSwap',
  components: { ParticleBackground },
  setup() {
    const route = useRoute(),
      stage = ref(null),
      swapping = ref(false)
    const sharedBackground = computed(() => authRoutes.has(route.name))
    let enabled = false,
      reducedMotion,
      outgoing,
      outgoingBox,
      settle,
      currentCard,
      generation = 0
    const animations = new Set()
    function cleanup() {
      generation++
      for (const animation of animations) animation.cancel()
      animations.clear()
      outgoing?.remove()
      outgoing = null
      if (currentCard) {
        currentCard.inert = false
        currentCard = null
      }
      swapping.value = false
      const done = settle
      settle = null
      done?.()
    }
    watch(
      () => route.name,
      (next, previous) => {
        enabled = next !== previous && authRoutes.has(next) && authRoutes.has(previous)
      },
      { flush: 'sync' },
    )
    function leave(element, done) {
      cleanup()
      const card = element.querySelector('.auth-card')
      if (enabled && !reducedMotion?.matches && card) {
        outgoingBox = card.getBoundingClientRect()
        outgoing = card.cloneNode(true)
        outgoing.inert = true
        outgoing.setAttribute('aria-hidden', 'true')
        outgoing.removeAttribute('id')
        outgoing.querySelectorAll('[id]').forEach((node) => node.removeAttribute('id'))
        Object.assign(outgoing.style, {
          position: 'fixed',
          left: outgoingBox.x + 'px',
          top: outgoingBox.y + 'px',
          width: outgoingBox.width + 'px',
          height: outgoingBox.height + 'px',
          margin: '0',
          pointerEvents: 'none',
          zIndex: '3',
          transformOrigin: 'center center',
        })
        stage.value.appendChild(outgoing)
      }
      // The outgoing card is an inert snapshot: only the incoming form stays mounted.
      done()
    }
    function enter(element, done) {
      const card = element.querySelector('.auth-card')
      if (!outgoing || !card || reducedMotion?.matches) {
        cleanup()
        done()
        return
      }
      const token = generation,
        rect = card.getBoundingClientRect()
      const lift = Math.min(CardSwapMotion.MAX_LIFT, outgoingBox.height * CardSwapMotion.LIFT_RATIO)
      const backX = rect.x - outgoingBox.x + CardSwapMotion.BACK_X
      const backY = rect.y - outgoingBox.y + CardSwapMotion.BACK_Y
      const back = `translate3d(${backX}px, ${backY}px, -${CardSwapMotion.BACK_DEPTH}px) scale(${CardSwapMotion.BACK_SCALE})`
      swapping.value = true
      currentCard = card
      card.inert = true
      settle = done
      const options = {
        duration: CardSwapMotion.DURATION,
        easing: CardSwapMotion.EASING,
        fill: 'both',
      }
      const departing = outgoing.animate(
        [
          { transform: 'translate3d(0, 0, 0)', opacity: 1, zIndex: 3, offset: 0 },
          {
            transform: `translate3d(0, -${lift}px, 80px) rotate(-2deg)`,
            opacity: 1,
            zIndex: 3,
            offset: 0.4,
          },
          {
            transform: `translate3d(${backX}px, -${lift}px, -${CardSwapMotion.BACK_DEPTH}px) scale(${CardSwapMotion.BACK_SCALE})`,
            opacity: 0.9,
            zIndex: 0,
            offset: 0.58,
          },
          { transform: back, opacity: 0.8, zIndex: 0, offset: 0.88 },
          { transform: back, opacity: 0, zIndex: 0, offset: 1 },
        ],
        options,
      )
      const arriving = card.animate(
        [
          {
            transform: `translate3d(10px, 22px, -${CardSwapMotion.BACK_DEPTH}px) scale(${CardSwapMotion.BACK_SCALE})`,
            opacity: 0.8,
            offset: 0,
          },
          { transform: 'translate3d(0, 0, 0) scale(1)', opacity: 1, offset: 0.78 },
          { transform: 'translate3d(0, 0, 0) scale(1)', opacity: 1, offset: 1 },
        ],
        options,
      )
      animations.add(departing)
      animations.add(arriving)
      Promise.all([departing.finished, arriving.finished])
        .then(() => {
          if (token !== generation) return
          cleanup()
          const heading = element.querySelector('h1')
          if (heading) {
            heading.setAttribute('tabindex', '-1')
            heading.focus({ preventScroll: true })
          }
        })
        .catch(() => {
          if (token === generation) cleanup()
        })
    }
    onMounted(() => {
      reducedMotion = matchMedia('(prefers-reduced-motion: reduce)')
      reducedMotion.addEventListener('change', cleanup)
    })
    onUnmounted(() => {
      reducedMotion?.removeEventListener('change', cleanup)
      cleanup()
    })
    return { stage, swapping, sharedBackground, leave, enter }
  },
})
