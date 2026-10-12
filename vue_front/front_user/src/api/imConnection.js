import { Im } from '@/constants/im'
// 全站共享连接，账号切换时完整释放计时器和监听器
export function connectIm({ frame, connected, state }) {
  let socket,
    retry,
    heartbeat,
    stopped = false,
    delay = Im.RECONNECT_MIN,
    lastPong = 0,
    watching = null
  function send(type, data = {}) {
    if (socket?.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type, data }))
  }
  function open() {
    if (stopped) return
    state('connecting')
    const url = new URL((import.meta.env.VITE_API_BASE || '') + '/im/ws', window.location.href)
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
    socket = new WebSocket(url)
    socket.onopen = () => {
      if (stopped) return
      delay = Im.RECONNECT_MIN
      lastPong = Date.now()
      state('connected')
      if (watching) send('presence.watch', { userId: watching })
      void connected()
      heartbeat = setInterval(() => {
        if (Date.now() - lastPong > Im.HEARTBEAT_TIMEOUT) socket.close()
        else send('ping')
      }, Im.HEARTBEAT_MS)
    }
    socket.onmessage = (event) => {
      if (stopped) return
      let value
      try {
        value = JSON.parse(event.data)
      } catch {
        return
      }
      if (value.type === 'pong') lastPong = Date.now()
      else frame(value)
    }
    socket.onerror = () => socket.close()
    socket.onclose = () => {
      clearInterval(heartbeat)
      if (stopped) return
      state('reconnecting')
      retry = setTimeout(open, delay)
      delay = Math.min(Im.RECONNECT_MAX, delay * 2)
    }
  }
  const visibility = () => {
    if (!document.hidden && socket?.readyState === WebSocket.OPEN) void connected()
  }
  document.addEventListener('visibilitychange', visibility)
  open()
  return {
    watch(id) {
      watching = id
      send(id ? 'presence.watch' : 'presence.unwatch', id ? { userId: id } : {})
    },
    stop() {
      stopped = true
      clearTimeout(retry)
      clearInterval(heartbeat)
      document.removeEventListener('visibilitychange', visibility)
      socket?.close()
    },
  }
}
