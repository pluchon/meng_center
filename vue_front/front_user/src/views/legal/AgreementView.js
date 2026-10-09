import BorderGlow from '@/components/border-glow/BorderGlow.vue'
import CardEdge from '@/components/card-edge/CardEdge.vue'
import { defineComponent, ref, computed, watch, nextTick, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { brandIcon, legalArtwork } from '@/assets/images'
import ParticleBackground from '@/components/particle-background/ParticleBackground.vue'
import terms from '@/content/legal/terms'
import privacy from '@/content/legal/privacy'
import { LegalDocument } from '@/constants/app'
import { RouteName } from '@/router/names'
export default defineComponent({
  name: 'AgreementView',
  components: { BorderGlow, CardEdge, ParticleBackground },
  setup() {
    const route = useRoute(),
      selected = ref(
        route.query.document === LegalDocument.PRIVACY
          ? LegalDocument.PRIVACY
          : LegalDocument.TERMS,
      ),
      scrollArea = ref(null),
      active = ref('publication'),
      progress = ref(0)
    const returnRoute = computed(() =>
      route.query.from === RouteName.LOGIN ? RouteName.LOGIN : RouteName.REGISTER,
    )
    const document = computed(() => (selected.value === LegalDocument.PRIVACY ? privacy : terms))
    function jump(id) {
      const section = scrollArea.value?.querySelector('#' + id)
      if (section) scrollArea.value.scrollTop = section.offsetTop - scrollArea.value.offsetTop
      active.value = id
    }
    function updateActive() {
      if (!scrollArea.value) return
      const area = scrollArea.value
      const remaining = area.scrollHeight - area.clientHeight
      progress.value =
        remaining <= 0 ? 100 : Math.min(100, Math.round((area.scrollTop / remaining) * 100))
      const top = area.getBoundingClientRect().top
      for (const section of scrollArea.value.querySelectorAll('section'))
        if (section.getBoundingClientRect().top <= top + 60) active.value = section.id
    }
    watch(selected, async () => {
      await nextTick()
      jump(document.value.sections[0].id)
      updateActive()
    })
    onMounted(updateActive)
    return {
      t: useI18n().t,
      document,
      returnRoute,
      progress,
      brandIcon,
      legalArtwork,
      selected,
      scrollArea,
      active,
      jump,
      updateActive,
      RouteName,
      LegalDocument,
    }
  },
})
