import { computed, defineComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { messageChevronIcon, growthCalendarIcon, growthCheckIcon } from '@/assets/images'
import { WeekTones } from '@/constants/growth'
export default defineComponent({
  name: 'GrowthCalendar',
  props: {
    data: { type: Object, required: true },
    checked: Boolean,
    sending: Boolean,
    reward: { type: Number, default: null },
  },
  emits: ['month', 'check'],
  setup(props, { emit }) {
    const { t, tm, rt } = useI18n()
    const weekdays = computed(() => tm('growth.weekdays').map((value) => rt(value)))
    const cells = computed(() => {
      const [year, month] = props.data.month.split('-').map(Number)
      const offset = (new Date(Date.UTC(year, month - 1, 1)).getUTCDay() + 6) % 7
      const count = new Date(Date.UTC(year, month, 0)).getUTCDate()
      const signed = new Set(props.data.dates)
      return [
        ...Array.from({ length: offset }, (_, index) => ({ key: 'blank' + index })),
        ...Array.from({ length: count }, (_, index) => {
          const day = index + 1,
            date = props.data.month + '-' + String(day).padStart(2, '0')
          return {
            key: date,
            day,
            checked: signed.has(date),
            today: date === props.data.today,
            future: date > props.data.today,
            tone: WeekTones[(offset + index) % 7],
          }
        }),
        ...Array.from({ length: 42 - offset - count }, (_, index) => ({ key: 'tail' + index })),
      ]
    })
    const monthLabel = computed(() => {
      const [year, month] = props.data.month.split('-').map(Number)
      return t('growth.month', { year, month })
    })
    function move(delta) {
      const [year, month] = props.data.month.split('-').map(Number)
      const date = new Date(Date.UTC(year, month - 1 + delta, 1))
      emit('month', date.toISOString().slice(0, 7))
    }
    return {
      t,
      weekdays,
      cells,
      monthLabel,
      move,
      icons: { chevron: messageChevronIcon, calendar: growthCalendarIcon, check: growthCheckIcon },
    }
  },
})
