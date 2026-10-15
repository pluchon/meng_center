import { computed, defineComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  growthCalendarIcon,
  growthCheckIcon,
  editorPencilIcon,
  creatorCommentsIcon,
} from '@/assets/images'
import { ExpSource, SourceTones, WeekTones } from '@/constants/growth'
import { RouteName } from '@/router/names'
import PageState from '@/components/page-state/PageState.vue'
export default defineComponent({
  name: 'GrowthTasks',
  components: { PageState },
  props: { tasks: { type: Array, required: true }, stats: { type: Object, required: true } },
  emits: ['retry'],
  setup(props) {
    const { t, tm, rt } = useI18n()
    const icons = {
      CHECK_IN: growthCalendarIcon,
      POST: editorPencilIcon,
      COMMENT: creatorCommentsIcon,
    }
    const weekly = computed(() => props.stats.data?.days ?? [])
    const maximum = computed(() => Math.max(1, ...weekly.value.map((row) => row.amount)))
    function weekday(date) {
      return rt(tm('growth.weekdays')[(new Date(date + 'T12:00:00Z').getUTCDay() + 6) % 7])
    }
    return {
      t,
      icons,
      checkIcon: growthCheckIcon,
      ExpSource,
      SourceTones,
      weekdayTone: (date) => WeekTones[(new Date(date + 'T12:00:00Z').getUTCDay() + 6) % 7],
      RouteName,
      weekly,
      maximum,
      weekday,
      total: computed(() => weekly.value.reduce((sum, row) => sum + row.amount, 0)),
    }
  },
})
