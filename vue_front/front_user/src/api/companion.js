import request, { normalizeFailure, settleFailure } from './request'
import { readCompanionStream } from './companionStream'
import { CompanionError } from '@/constants/meng'
const root = '/companion'
const silent = { silent: true }
export const companionApi = {
  characters: (config) => request.get(root + '/characters', config),
  sessions: (code, params, config) =>
    request.get(root + '/characters/' + code + '/sessions', { ...config, params }),
  createSession: (code) => request.post(root + '/characters/' + code + '/sessions', null, silent),
  deleteSession: (id) => request.delete(root + '/sessions/' + id),
  messages: (id, params, config) =>
    request.get(root + '/sessions/' + id + '/messages', { ...config, params }),
  memories: (code, config) => request.get(root + '/characters/' + code + '/memories', config),
  deleteMemory: (id) => request.delete(root + '/memories/' + id),
  clearMemories: (code) => request.delete(root + '/characters/' + code + '/memories'),
  reset: (code) => request.post(root + '/characters/' + code + '/reset'),
  works: (params, config) => request.get(root + '/works', { ...config, params }),
  work: (id, config) => request.get(root + '/works/' + id, config),
  deleteWork: (id) => request.delete(root + '/works/' + id),
  usage: () => request.get(root + '/usage', silent),
  upload: (file) => {
    const body = new FormData()
    body.append('file', file)
    return request.post(root + '/images', body, silent)
  },
}
export const companionImage = (id) => (import.meta.env.VITE_API_BASE || '') + root + '/images/' + id
export const companionFile = (id) =>
  (import.meta.env.VITE_API_BASE || '') + root + '/works/' + id + '/file'
export const companionBlob = (id) =>
  request.get(root + '/works/' + id + '/file', { responseType: 'blob', rawData: true })

// POST 流使用 Cookie；按 SSE 的事件边界解码，保留跨网络分块的中文和 CRLF。
// 失败和 axios 走同一套处理（settleFailure）：未登录跳登录页，其余整理成带文案的错误交给聊天区展示。
export async function sendCompanionMessage(id, body, { signal, onEvent }) {
  let response
  try {
    response = await fetch(
      (import.meta.env.VITE_API_BASE || '') + root + '/sessions/' + id + '/messages',
      {
        method: 'POST',
        credentials: 'include',
        signal,
        headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
        body: JSON.stringify(body),
      },
    )
  } catch {
    if (signal.aborted) throw { cancelled: true }
    throw await settleFailure(0, {}, silent)
  }
  if (!response.ok)
    throw await settleFailure(response.status, await response.json().catch(() => ({})), silent)
  if (!response.headers.get('content-type')?.includes('text/event-stream'))
    throw normalizeFailure(503, { code: CompanionError.UNAVAILABLE })
  try {
    await readCompanionStream(response.body, { signal, onEvent })
  } catch (error) {
    throw error.cancelled || error.message ? error : normalizeFailure(error.code === 'NETWORK' ? 0 : null, error)
  }
}
