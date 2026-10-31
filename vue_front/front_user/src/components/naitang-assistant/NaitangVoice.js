import { computed, defineComponent } from 'vue'
import { useI18n } from 'vue-i18n'

export default defineComponent({
  name: 'NaitangVoice',
  props: {
    ms: { type: Number, required: true },
    playing: { type: Boolean, default: false },
    inline: { type: Boolean, default: false },
  },
  emits: ['toggle'],
  setup(props) {
    const { t } = useI18n()
    // 不足一秒也显示 1″，超过一分钟写成 1′05″
    const duration = computed(() => {
      const seconds = Math.max(1, Math.round(props.ms / 1000))
      return seconds < 60
        ? seconds + '″'
        : Math.floor(seconds / 60) + '′' + String(seconds % 60).padStart(2, '0') + '″'
    })
    return { t, duration }
  },
})
