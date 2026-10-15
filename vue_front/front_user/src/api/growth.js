import request from './request'
const Root = '/growth'
export const growthApi = {
  me: () => request.get(Root + '/me'),
  checkIn: () => request.post(Root + '/check-ins'),
  checkIns: (month, config = {}) => request.get(Root + '/check-ins', { ...config, params: { month } }),
  overview: (config = {}) => request.get(Root + '/overview', config),
  stats: (days, config = {}) => request.get(Root + '/exp-stats', { ...config, params: { days } }),
  records: (params, config = {}) => request.get(Root + '/exp-records', { ...config, params }),
  points: (config = {}) => request.get(Root + '/points', config),
  pointStats: (days, config = {}) =>
    request.get(Root + '/point-stats', { ...config, params: { days } }),
  pointRecords: (params, config = {}) =>
    request.get(Root + '/point-records', { ...config, params }),
}
