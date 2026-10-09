import { RouteName } from './names'
export const routes = [
  { path: '/', redirect: { name: RouteName.REVIEW } },
  {
    path: '/login',
    name: RouteName.LOGIN,
    component: () => import('@/views/login/LoginView.vue'),
    meta: { titleKey: 'auth.login.adminTitle' },
  },
  {
    path: '/registrations',
    name: RouteName.REVIEW,
    component: () => import('@/views/review/ReviewView.vue'),
    meta: { titleKey: 'user.review.title', requiresAuth: true, admin: true },
  },
  { path: '/:pathMatch(.*)*', redirect: { name: RouteName.REVIEW } },
]
