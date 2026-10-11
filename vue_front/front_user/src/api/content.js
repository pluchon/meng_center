import request from './request'
const Root = '/content'
export const contentApi = {
  update: (id, body) => request.put(Root + '/posts/' + id, body),
  delete: (id) => request.delete(Root + '/posts/' + id),
  comments: (id, params) => request.get(Root + '/posts/' + id + '/comments', { params }),
  replies: (id, params) => request.get(Root + '/comments/' + id + '/replies', { params }),
  comment: (id, body) => request.post(Root + '/posts/' + id + '/comments', body),
  deleteComment: (id) => request.delete(Root + '/comments/' + id),
  commentLike: (id, on) =>
    on
      ? request.put(Root + '/comments/' + id + '/like')
      : request.delete(Root + '/comments/' + id + '/like'),
  report: (body) => request.post(Root + '/reports', body),
  uploadComment: (file, config) => {
    const body = new FormData()
    body.append('file', file)
    return request.post(Root + '/comment-images', body, config)
  },
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
