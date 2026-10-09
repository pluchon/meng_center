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
request.interceptors.response.use(
  (response) => response.data.data,
  async (error) => {
    const status = error.response?.status ?? 0
    const body = error.response?.data ?? {}
    const code = body.code ?? 'NETWORK'
    const key = 'error.' + code
    const message = i18n.global.te(key)
      ? i18n.global.t(key)
      : body.message || i18n.global.t('error.generic')
    const normalized = { status, code, message, data: body.data }
    if (!error.config?.silent) {
      if (status === 401 && !error.config?.loginRequest) {
        if (!unauthorizedPending)
          unauthorizedPending = Promise.resolve(unauthorizedHandler()).finally(() => {
            unauthorizedPending = null
          })
        await unauthorizedPending
      } else if (
        status === 429 ||
        status >= 500 ||
        !status ||
        (status === 403 && !error.config?.loginRequest)
      ) {
        ElMessage.error(message)
      }
    }
    return Promise.reject(normalized)
  },
)
export default request
