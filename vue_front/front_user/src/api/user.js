import request from './request'
const Root = '/user'
export const userApi = Object.freeze({
  profileReview: (config = {}) =>
    request.get(Root + '/me/profile-review', { silent: true, ...config }),
  me: (silent = false, config = {}) => request.get(Root + '/me', { ...config, silent }),
  login: (data) => request.post(Root + '/sessions', data, { loginRequest: true }),
  logout: () => request.delete(Root + '/sessions/current'),
  captcha: () => request.post(Root + '/captcha'),
  verifyCaptcha: (data) => request.post(Root + '/captcha/verification', data),
  emailCode: (data) => request.post(Root + '/email-codes', data),
  smsCode: (data) => request.post(Root + '/sms-codes', data),
  resetCode: (data) => request.post(Root + '/password-reset-codes', data),
  resetPassword: (data) => request.post(Root + '/password-resets', data),
  register: (data) => request.post(Root + '/registrations', data),
  reapply: (data) => request.post(Root + '/registrations/reapplication', data),
  password: (data) => request.put(Root + '/me/password', data),
  profile: (data) => request.put(Root + '/me/profile', data),
  avatar: (file) => {
    const body = new FormData()
    body.append('file', file)
    return request.post(Root + '/me/avatar', body)
  },
  contactCode: (data) => request.post(Root + '/me/contact-codes', data),
  email: (data) => request.put(Root + '/me/email', data),
  phone: (data) => request.put(Root + '/me/phone', data),
  devices: (config = {}) => request.get(Root + '/me/sessions', config),
  revokeDevice: (id) => request.delete(Root + '/me/sessions/' + id),
  revokeOthers: () => request.delete(Root + '/me/sessions/others'),
  publicProfile: (id, config = {}) => request.get(Root + '/users/' + id, config),
  follow: (id, on) =>
    on
      ? request.put(Root + '/users/' + id + '/follow')
      : request.delete(Root + '/users/' + id + '/follow'),
  following: (id, params, config = {}) =>
    request.get(Root + '/users/' + id + '/following', { ...config, params }),
  followers: (id, params, config = {}) =>
    request.get(Root + '/users/' + id + '/followers', { ...config, params }),
})
export function avatarUrl(value) {
  return value?.startsWith('/user/avatars/') ? (import.meta.env.VITE_API_BASE || '') + value : value
}
