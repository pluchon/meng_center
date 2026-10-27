import request, { normalizeFailure, settleFailure } from './request'
import { readCompanionStream } from './companionStream'
import { CompanionError } from '@/constants/companion'
const root = '/companion'
export function companionTimeZone() {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Taipei'
}
const headers = () => ({ 'X-Time-Zone': companionTimeZone() })
const scoped = (config = {}) => ({ ...config, headers: { ...config.headers, ...headers() } })
const silent = { silent: true }
export const companionApi = {
  sessions: (params, config) =>
    request.get(root + '/sessions', scoped({ ...config, params, silent: true })),
  createSession: () => request.post(root + '/sessions', null, scoped(silent)),
  deleteSession: (id) => request.delete(root + '/sessions/' + id, scoped(silent)),
  messages: (id, params, config) =>
    request.get(root + '/sessions/' + id + '/messages', scoped({ ...config, params })),
  clearMemories: () => request.delete(root + '/memories', scoped(silent)),
  usage: () => request.get(root + '/usage', scoped(silent)),
  upload: (file) => {
    const body = new FormData()
    body.append('file', file)
    return request.post(root + '/images', body, scoped(silent))
  },
}
export const companionImage = (id) => (import.meta.env.VITE_API_BASE || '') + root + '/images/' + id
// POST 流使用 Cookie；按 SSE 的事件边界解码，保留跨网络分块的中文和 CRLF。
// 失败和 axios 走同一套处理（settleFailure）：未登录跳登录页，其余整理成带文案的错误交给聊天区展示。
export async function sendCompanionMessage(id, body, { signal, onEvent }) {
  return streamCompanion(root + '/sessions/' + id + '/messages', body, { signal, onEvent })
}
async function streamCompanion(path, body, { signal, onEvent }) {
  let response
  try {
    response = await fetch((import.meta.env.VITE_API_BASE || '') + path, {
      method: 'POST',
      credentials: 'include',
      signal,
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream, application/json',
        ...headers(),
      },
      body: JSON.stringify(body),
    })
  } catch {
    if (signal?.aborted) throw { cancelled: true }
    throw await settleFailure(0, {}, silent)
  }
  if (!response.ok)
    throw await settleFailure(response.status, await response.json().catch(() => ({})), silent)
  if (!response.headers.get('content-type')?.includes('text/event-stream'))
    throw normalizeFailure(503, { code: CompanionError.UNAVAILABLE })
  try {
    await readCompanionStream(response.body, { signal, onEvent })
  } catch (error) {
    throw error.cancelled || error.message
      ? error
      : normalizeFailure(error.code === 'NETWORK' ? 0 : null, error)
  }
}
