import { defineComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import BorderGlow from '@/components/border-glow/BorderGlow.vue'
import CardEdge from '@/components/card-edge/CardEdge.vue'
import ParticleBackground from '@/components/particle-background/ParticleBackground.vue'
import { statePage404Art } from '@/assets/images'
import { RouteName } from '@/router/names'
export default defineComponent({
  name: 'NotFoundView',
  components: { BorderGlow, CardEdge, ParticleBackground },
  setup() {
    return { t: useI18n().t, RouteName, statePage404Art }
  },
})
