import { useUserStore } from '@/stores/user'
import { i18n } from '@/locales'
import { RouteName } from './names'
import { UserRole, LegalDocument } from '@/constants/app'
export function registerGuards(router) {
  router.beforeEach(async (to) => {
    const user = useUserStore()
    if (!user.restored) {
      try {
        await user.restore()
      } catch {
        /* The page retains its retry state. */
      }
    }
    if (to.meta.requiresAuth && !user.loggedIn)
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
