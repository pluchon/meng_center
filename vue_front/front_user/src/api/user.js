import request from './request'
const Root = '/user'
export const userApi = Object.freeze({
  me: (silent = false) => request.get(Root + '/me', { silent }),
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
})
