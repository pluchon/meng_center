import { onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RelativeTime } from '@/constants/content'
export function usePostTime() {
  const now = ref(Date.now()),
    { t } = useI18n()
  let timer
  onMounted(() => {
    timer = setInterval(() => (now.value = Date.now()), RelativeTime.MINUTE_MS)
  })
  onUnmounted(() => clearInterval(timer))
  return (value) => {
    const utc = /(Z|[+-]\d\d:\d\d)$/.test(value) ? value : value + 'Z'
    const minutes = Math.max(0, Math.floor((now.value - Date.parse(utc)) / RelativeTime.MINUTE_MS))
    if (!Number.isFinite(minutes) || minutes < 1) return t('home.time.justNow')
    if (minutes < RelativeTime.HOUR_MINUTES) return t('home.time.minutes', { count: minutes })
    const hours = Math.floor(minutes / RelativeTime.HOUR_MINUTES)
    if (hours < RelativeTime.DAY_HOURS) return t('home.time.hours', { count: hours })
    return t('home.time.days', { count: Math.floor(hours / RelativeTime.DAY_HOURS) })
  }
}
