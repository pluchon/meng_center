import { useUserStore } from '@/stores/user'
import { i18n } from '@/locales'
import { RouteName } from './names'
import { UserRole } from '@/constants/app'
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
    document.title = i18n.global.t(to.meta.titleKey) + ' · ' + i18n.global.t('common.brand')
  })
}
