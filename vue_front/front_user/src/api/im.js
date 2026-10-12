import request from './request'
const Root = '/im'
export const imApi = {
  unread: () => request.get(Root + '/unread', { silent: true }),
  conversations: (params, config = {}) =>
    request.get(Root + '/conversations', { ...config, params }),
  peer: (id, config = {}) => request.get(Root + '/conversations/peer/' + id, config),
  messages: (id, params, config = {}) =>
    request.get(Root + '/conversations/' + id + '/messages', { ...config, params }),
  send: (body) => request.post(Root + '/messages', body, { silent: true }),
  read: (id, messageId) =>
    request.put(Root + '/conversations/' + id + '/read', { messageId }, { silent: true }),
  delete: (id) => request.delete(Root + '/conversations/' + id),
  block: (id) => request.post(Root + '/blocks', { userId: id }),
  unblock: (id) => request.delete(Root + '/blocks/' + id),
  settings: () => request.get(Root + '/settings', { silent: true }),
  setting: (hidden) => request.put(Root + '/settings', { hidden }),
  groups: (channel, config = {}) =>
    request.get(Root + '/notification-groups', { ...config, params: { channel } }),
  notices: (params, config = {}) => request.get(Root + '/notifications', { ...config, params }),
  readNotices: (body) => request.put(Root + '/notifications/read', body),
  upload: async (file, config) => {
    const body = new FormData()
    body.append('file', file)
    const image = await request.post(Root + '/images', body, config)
    return { ...image, thumbUrl: Root + '/images/' + image.id + '?thumb=true' }
  },
}
export function imImage(id, thumb = false) {
  return (
    (import.meta.env.VITE_API_BASE || '') + Root + '/images/' + id + (thumb ? '?thumb=true' : '')
  )
}
