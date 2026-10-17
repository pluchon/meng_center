import { CompanionEvent } from '../constants/meng.js'

// SSE 只按空行分隔事件，UTF-8 解码跨网络分块保留；上层决定如何展示事件。
// 已经开始输出后的 error 事件和中途断流抛出 { code }，由调用方整理成请求层的错误对象。
export async function readCompanionStream(body, { signal, onEvent }) {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let pending = '',
    completed = false
  async function consume() {
    let match
    while ((match = /\r?\n\r?\n/.exec(pending))) {
      const block = pending.slice(0, match.index)
      pending = pending.slice(match.index + match[0].length)
      let event = 'message'
      const data = []
      for (const line of block.split(/\r?\n/)) {
        if (line.startsWith('event:')) event = line.slice(6).trim()
        if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
      }
      if (!data.length) continue
      const payload = JSON.parse(data.join('\n'))
      if (event === CompanionEvent.ERROR) throw payload
      if (event === CompanionEvent.DONE) completed = true
      await onEvent(event, payload)
    }
  }
  try {
    while (true) {
      let chunk
      try {
        chunk = await reader.read()
      } catch {
        if (signal.aborted) throw { cancelled: true }
        throw { code: 'NETWORK' }
      }
      pending += decoder.decode(chunk.value, { stream: !chunk.done })
      await consume()
      if (chunk.done) break
    }
    if (!completed && !signal.aborted) throw { code: 'NETWORK' }
  } finally {
    await reader.cancel().catch(() => {})
    reader.releaseLock()
  }
}
