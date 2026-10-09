import request from './request'
const Root = '/user'
export const userApi = Object.freeze({
  me: (silent = false) => request.get(Root + '/me', { silent }),
  login: (data) => request.post(Root + '/sessions', data, { loginRequest: true }),
  logout: () => request.delete(Root + '/sessions/current'),
  captcha: () => request.post(Root + '/captcha'),
  verifyCaptcha: (data) => request.post(Root + '/captcha/verification', data),
  registrations: (params) => request.get(Root + '/admin/registrations', { params }),
  approve: (id) => request.post(Root + '/admin/registrations/' + id + '/approval'),
  reject: (id, reason) =>
    request.post(Root + '/admin/registrations/' + id + '/rejection', { reason }),
})
