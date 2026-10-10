import { defineComponent } from 'vue'
import { Check } from '@element-plus/icons-vue'
import { useCommunity } from '@/composables/useCommunity'
import { useGrowthStore } from '@/stores/growth'
import { notifyFailure } from '@/composables/useFailureNotice'
import PageState from '@/components/page-state/PageState.vue'
export default defineComponent({
  name: 'CheckInCard',
  components: { Check, PageState },
  setup() {
    const { t } = useCommunity(),
      growth = useGrowthStore()
    async function checkIn() {
      try {
        await growth.checkIn()
      } catch (e) {
        notifyFailure(e)
      }
    }
    return { t, growth, checkIn }
  },
})
