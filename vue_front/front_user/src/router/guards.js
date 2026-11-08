import { useUserStore } from '@/stores/user'
import { i18n } from '@/locales'
import { RouteName } from './names'
import { UserRole, LegalDocument } from '@/constants/app'
// 已登录时再进这些页面没有意义，直接回主页
const GuestRoutes = [RouteName.LOGIN, RouteName.REGISTER, RouteName.RESET_PASSWORD]
export function registerGuards(router) {
  router.beforeEach(async (to) => {
    const user = useUserStore()
    if (!user.restored) {
      try {
        await user.restore()
      } catch {
        // 不知道登录状态（服务暂不可用或断网），不当成未登录踢去登录页；页面自己的请求会给出错误和重试
      }
    }
    if (user.loggedIn && GuestRoutes.includes(to.name)) return { name: RouteName.HOME }
    if (to.meta.requiresAuth && !user.loggedIn && user.restored)
      return { name: RouteName.LOGIN, query: { redirect: to.fullPath } }
    if (to.meta.admin && user.current?.role !== UserRole.ADMIN) return { name: RouteName.LOGIN }
  })
  router.afterEach((to, from, failure) => {
    if (failure) return
    const titleKey =
      to.name === RouteName.AGREEMENT && to.query.document === LegalDocument.PRIVACY
        ? 'auth.register.privacy'
        : to.meta.titleKey
    document.title =
      to.name === RouteName.HOME
        ? i18n.global.t('common.homePageTitle')
        : i18n.global.t('common.pageTitle', { page: i18n.global.t(titleKey) })
  })
}
