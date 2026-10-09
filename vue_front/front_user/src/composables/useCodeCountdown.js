import { ref, onMounted, onUnmounted } from 'vue'
import { Auth, StorageKey } from '@/constants/app'

// 发码倒计时。结束时刻记在 sessionStorage：刷新页面后接着数，
// 不会让人白拖一次滑块再被服务端的发送间隔挡回来；按时刻计算，标签页切到后台也不会数慢
export function useCodeCountdown(scope) {
  const seconds = ref(0)
  const key = StorageKey.CODE_COUNTDOWN + scope
  let timer
  function recall() {
    try {
      return Number(sessionStorage.getItem(key)) || 0
    } catch {
      // 浏览器禁用存储时只是不记，倒计时照常
      return 0
    }
  }
  function remember(endsAt) {
    try {
      if (endsAt) sessionStorage.setItem(key, String(endsAt))
      else sessionStorage.removeItem(key)
    } catch {
      // 同上
    }
  }
  function run(endsAt) {
    clearInterval(timer)
    const update = () => {
      seconds.value = Math.max(0, Math.ceil((endsAt - Date.now()) / 1000))
      if (seconds.value) return
      clearInterval(timer)
      remember(0)
    }
    update()
    if (seconds.value) timer = setInterval(update, 1000)
  }
  function start() {
    const endsAt = Date.now() + Auth.CODE_SECONDS * 1000
    remember(endsAt)
    run(endsAt)
  }
  function stop() {
    clearInterval(timer)
    seconds.value = 0
    remember(0)
  }
  onMounted(() => run(recall()))
  onUnmounted(() => clearInterval(timer))
  return { seconds, start, stop }
}
