import { RouteName } from './names'
export const routes = [
  { path: '/', redirect: { name: RouteName.LOGIN } },
  {
    path: '/login',
    name: RouteName.LOGIN,
    component: () => import('@/views/auth/login/LoginView.vue'),
    meta: { titleKey: 'auth.login.title' },
  },
  {
    path: '/register',
    name: RouteName.REGISTER,
    component: () => import('@/views/auth/register/RegisterView.vue'),
    meta: { titleKey: 'auth.register.title' },
  },
  {
    path: '/reset-password',
    name: RouteName.RESET_PASSWORD,
    component: () => import('@/views/auth/reset/ResetPasswordView.vue'),
    meta: { titleKey: 'auth.reset.title' },
  },
  {
    path: '/agreement',
    name: RouteName.AGREEMENT,
    component: () => import('@/views/legal/AgreementView.vue'),
    meta: { titleKey: 'auth.register.terms' },
  },
  {
    path: '/account',
    name: RouteName.ACCOUNT,
    component: () => import('@/views/account/AccountView.vue'),
    meta: { titleKey: 'user.account.title', requiresAuth: true },
  },
  { path: '/:pathMatch(.*)*', redirect: { name: RouteName.LOGIN } },
]
