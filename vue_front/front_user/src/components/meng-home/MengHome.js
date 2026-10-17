import { defineComponent, inject, toRefs } from 'vue'
import {
  IconSparkles,
  IconMessageCircle,
  IconFiles,
  IconHeart,
  IconHeartFilled,
  IconCalendar,
  IconPalette,
  IconPlant,
  IconSnowflake,
  IconSun,
  IconCat,
} from '@tabler/icons-vue'
import { CompanionContext } from '@/constants/meng'
import {
  companionWaveBack,
  companionWaveFront,
  companionCloud,
  companionAiGuideChibi,
} from '@/assets/images'
export default defineComponent({
  name: 'MengHome',
  components: {
    IconSparkles,
    IconMessageCircle,
    IconFiles,
    IconHeart,
    IconHeartFilled,
    IconCalendar,
    IconPalette,
    IconPlant,
  },
  setup() {
    return {
      ...toRefs(inject(CompanionContext)),
      symbols: [IconSnowflake, IconSun, IconCat],
      cloud: companionCloud,
      waveBack: companionWaveBack,
      waveFront: companionWaveFront,
      aiGuide: companionAiGuideChibi,
    }
  },
})
