import { createI18n } from 'vue-i18n'
import common from './zh-CN/common'
import auth from './zh-CN/auth'
import error from './zh-CN/error'
import user from './zh-CN/user'
export const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  fallbackLocale: 'zh-CN',
  messages: { 'zh-CN': { common, auth, error, user } },
})
