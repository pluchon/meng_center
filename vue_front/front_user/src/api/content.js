import request from './request'
const Root = '/content'
export const contentApi = {
  publish: (body) => request.post(Root + '/posts', body),
  detail: (id) => request.get(Root + '/posts/' + id),
  tags: (params) => request.get(Root + '/tags', { params }),
  draft: (id) => request.get(Root + '/drafts/' + id),
  saveDraft: (id, body) =>
    id ? request.put(Root + '/drafts/' + id, body) : request.post(Root + '/drafts', body),
  upload: (file, config) => {
    const body = new FormData()
    body.append('file', file)
    return request.post(Root + '/images', body, config)
  },
  feed: (params) => request.get(Root + '/posts', { params }),
  like: (id, on) =>
    on
      ? request.put(Root + '/posts/' + id + '/like')
      : request.delete(Root + '/posts/' + id + '/like'),
  favorite: (id, on) =>
    on
      ? request.put(Root + '/posts/' + id + '/favorite')
      : request.delete(Root + '/posts/' + id + '/favorite'),
}
export function contentImage(path) {
  return (import.meta.env.VITE_API_BASE || '') + path
}
