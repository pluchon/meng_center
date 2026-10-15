import BorderGlow from '@/components/border-glow/BorderGlow.vue'
import CardEdge from '@/components/card-edge/CardEdge.vue'
import { defineComponent, ref, computed, watch, nextTick, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import {
  brandIcon,
  communitySparkle,
  detailFlower,
  growthCheckIcon,
  growthClockIcon,
  growthFlagIcon,
  legalArtwork,
  legalPrivacyIcon,
  legalTermsIcon,
} from '@/assets/images'
import { useSlider } from '@/composables/useSlider'
import ParticleBackground from '@/components/particle-background/ParticleBackground.vue'
import terms from '@/content/legal/terms'
import privacy from '@/content/legal/privacy'
import { LegalDocument } from '@/constants/app'
import { RouteName } from '@/router/names'
export default defineComponent({
  name: 'AgreementView',
  components: { BorderGlow, CardEdge, ParticleBackground },
  setup() {
    const { t } = useI18n()
    const route = useRoute(),
      selected = ref(
        route.query.document === LegalDocument.PRIVACY
          ? LegalDocument.PRIVACY
          : LegalDocument.TERMS,
      ),
      scrollArea = ref(null),
      toc = ref(null),
      active = ref('publication'),
      progress = ref(0)
    const returnRoute = computed(() =>
      [RouteName.HOME, RouteName.LOGIN].includes(route.query.from) ? route.query.from : RouteName.REGISTER,
    )
    const document = computed(() => (selected.value === LegalDocument.PRIVACY ? privacy : terms))
    // 目录里的贴纸跟着悬停和当前章节滑动
    const tocSlider = useSlider(toc, {
      item: 'button',
      active: '.active',
      deps: () => [selected.value, active.value],
    })
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
      window.document.title = t('common.pageTitle', { page: t(document.value.titleKey) })
      await nextTick()
      // 换一份协议从头读起，标题和阅读提示要看得到
      if (scrollArea.value) scrollArea.value.scrollTop = 0
      active.value = document.value.sections[0].id
      updateActive()
    })
    onMounted(updateActive)
    return {
      t,
      document,
      returnRoute,
      progress,
      brandIcon,
      communitySparkle,
      detailFlower,
      growthCheckIcon,
      growthClockIcon,
      growthFlagIcon,
      legalArtwork,
      legalPrivacyIcon,
      legalTermsIcon,
      tocSlider,
      toc,
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
