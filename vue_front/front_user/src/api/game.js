import request from './request'
const Root = '/game'
export const gameApi = {
  profile: (config = {}) => request.get(Root + '/profile', config),
  world: (config = {}) => request.get(Root + '/world', config),
  pickup: (body) => request.post(Root + '/pickups', body),
  purchase: (itemId) => request.post(Root + '/shop/purchases', { itemId }),
  equip: (hat, outfit) => request.put(Root + '/equipment', { hat, outfit }),
  shrine: () => request.post(Root + '/shrine'),
  save: (position) => request.put(Root + '/save', position, { silent: true }),
  // 离开页面时补存：页面卸载后普通请求会被取消，keepalive 的能发完
  saveOnLeave: (position) =>
    fetch(import.meta.env.VITE_API_BASE + Root + '/save', {
      method: 'PUT',
      keepalive: true,
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(position),
    }).catch(() => {}),
}
