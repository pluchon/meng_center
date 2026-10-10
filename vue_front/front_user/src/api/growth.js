import request from './request'
const Root = '/growth'
export const growthApi = {
  me: () => request.get(Root + '/me'),
  checkIn: () => request.post(Root + '/check-ins'),
}
