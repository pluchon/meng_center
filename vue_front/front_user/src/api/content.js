import request from './request'
const Root = '/content'
export const contentApi = {
  folders: (params, config = {}) => request.get(Root + '/favorite-folders', { ...config, params }),
  createFolder: (data) => request.post(Root + '/favorite-folders', data),
  updateFolder: (id, data) => request.put(Root + '/favorite-folders/' + id, data),
  deleteFolder: (id) => request.delete(Root + '/favorite-folders/' + id),
  folderPosts: (id, params, config = {}) =>
    request.get(Root + '/favorite-folders/' + id + '/posts', { ...config, params }),
  likedPosts: (params, config = {}) => request.get(Root + '/posts/liked', { ...config, params }),
  myCounts: (config = {}) => request.get(Root + '/me/counts', config),
  commentLocation: (id, size) =>
    request.get(Root + '/comments/' + id + '/location', { params: { size } }),
  mine: (params, config = {}) => request.get(Root + '/posts/mine', { ...config, params }),
  creatorOverview: (config = {}) => request.get(Root + '/creator/overview', config),
  creatorTrend: (days, config = {}) =>
    request.get(Root + '/creator/trend', { ...config, params: { days } }),
  creatorTopPosts: (config = {}) => request.get(Root + '/creator/top-posts', config),
  drafts: (config = {}) => request.get(Root + '/drafts', config),
  deleteDraft: (id) => request.delete(Root + '/drafts/' + id),
  search: (params, config = {}) => request.get(Root + '/search/posts', { ...config, params }),
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
  feed: (params, config = {}) => request.get(Root + '/posts', { ...config, params }),
  like: (id, on) =>
    on
      ? request.put(Root + '/posts/' + id + '/like')
      : request.delete(Root + '/posts/' + id + '/like'),
  postFolderIds: (postId) => request.get(Root + '/posts/' + postId + '/favorite-folders'),
  savePostFolders: (postId, folderIds) =>
    request.put(Root + '/posts/' + postId + '/favorite-folders', { folderIds }),
  addToFolder: (folderId, postId) =>
    request.put(Root + '/favorite-folders/' + folderId + '/posts/' + postId),
  removeFromFolder: (folderId, postId) =>
    request.delete(Root + '/favorite-folders/' + folderId + '/posts/' + postId),
}
export function contentImage(path) {
  return (import.meta.env.VITE_API_BASE || '') + path
}
