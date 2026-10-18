import axios from 'axios'
import { ElMessage } from 'element-plus'
import { i18n } from '@/locales'
import { Request } from '@/constants/app'
let unauthorizedHandler = () => {}
let unauthorizedPending = null
export function onUnauthorized(handler) {
  unauthorizedHandler = handler
}
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE,
  timeout: Request.TIMEOUT,
  withCredentials: true,
})
// 把后端的失败整理成 { status, code, message, data }，文案按业务码查 error.*
export function normalizeFailure(status, body = {}) {
  const code = body.code ?? 'NETWORK'
  const key = 'error.' + code
  const message = i18n.global.te(key)
    ? i18n.global.t(key)
    : body.message || i18n.global.t('error.generic')
  return { status, code, message, data: body.data }
}
// 全站统一的失败处理，axios 和 fetch 读流共用：
// 未登录交给登录跳转（loginRequest、optionalAuth 除外）；无权限、限流、服务异常、断网在顶部提示一次，silent 时由页面自己展示
export async function settleFailure(status, body, config = {}) {
  const failure = normalizeFailure(status, body)
  if (status === 401 && !config.loginRequest && !config.optionalAuth) {
    if (!unauthorizedPending)
      unauthorizedPending = Promise.resolve(unauthorizedHandler()).finally(() => {
        unauthorizedPending = null
      })
    await unauthorizedPending
    failure.notified = true
  } else if (
    !config.silent &&
    (status === 429 || status >= 500 || !status || (status === 403 && !config.loginRequest))
  ) {
    ElMessage.error(failure.message)
    // 告诉调用方这条已经提示过，页面不用再弹一次
    failure.notified = true
  }
  return failure
}
request.interceptors.response.use(
  (response) => (response.config.rawData ? response.data : response.data.data),
  async (error) => {
    if (error.code === 'ERR_CANCELED') return Promise.reject({ cancelled: true })
    return Promise.reject(
      await settleFailure(error.response?.status ?? 0, error.response?.data ?? {}, error.config),
    )
  },
)
export default request
