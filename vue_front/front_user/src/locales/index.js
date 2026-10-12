import { createI18n } from 'vue-i18n'
import common from './zh-CN/common'
import auth from './zh-CN/auth'
import error from './zh-CN/error'
import user from './zh-CN/user'
import home from './zh-CN/home'
import editor from './zh-CN/editor'
import search from './zh-CN/search'
import detail from './zh-CN/detail'
import creator from './zh-CN/creator'
export const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  fallbackLocale: 'zh-CN',
  messages: { 'zh-CN': { common, auth, error, user, home, editor, detail, search, creator } },
})
