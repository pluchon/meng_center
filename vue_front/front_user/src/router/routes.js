import { RouteName } from './names'
export const routes = [
  {
    path: '/messages',
    name: RouteName.MESSAGES,
    component: () => import('@/views/messages/MessagesView.vue'),
    meta: { titleKey: 'im.title', requiresAuth: true },
  },
  {
    path: '/growth',
    name: RouteName.GROWTH,
    component: () => import('@/views/growth/GrowthView.vue'),
    meta: { titleKey: 'common.growthPageTitle', requiresAuth: true },
  },
  {
    path: '/creator',
    name: RouteName.CREATOR,
    component: () => import('@/views/creator/CreatorView.vue'),
    meta: { titleKey: 'creator.title', requiresAuth: true },
  },
  {
    path: '/search',
    name: RouteName.SEARCH,
    component: () => import('@/views/search/SearchView.vue'),
    meta: { titleKey: 'search.title', requiresAuth: true },
  },
  {
    path: '/posts/:id(\\d+)/edit',
    name: RouteName.POST_EDIT,
    component: () => import('@/views/post/editor/PostEditorView.vue'),
    meta: { titleKey: 'editor.editPageTitle', requiresAuth: true },
  },
  {
    path: '/posts/new',
    name: RouteName.POST_NEW,
    component: () => import('@/views/post/editor/PostEditorView.vue'),
    meta: { titleKey: 'editor.pageTitle', requiresAuth: true },
  },
  {
    path: '/posts/:id(\\d+)',
    name: RouteName.POST_DETAIL,
    component: () => import('@/views/post/detail/PostDetailView.vue'),
    meta: { titleKey: 'editor.detailTitle', requiresAuth: true },
  },
  {
    path: '/',
    name: RouteName.HOME,
    component: () => import('@/views/home/HomeView.vue'),
    meta: { titleKey: 'home.title', requiresAuth: true },
  },
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
    meta: { titleKey: 'common.registerPageTitle' },
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
    meta: { titleKey: 'account.title', requiresAuth: true },
  },
  {
    path: '/users/:id(\\d+)',
    name: RouteName.USER,
    component: () => import('@/views/account/AccountView.vue'),
    meta: { titleKey: 'account.otherTitle', requiresAuth: true },
  },
  {
    path: '/:pathMatch(.*)*',
    name: RouteName.NOT_FOUND,
    component: () => import('@/views/not-found/NotFoundView.vue'),
    meta: { titleKey: 'common.notFoundTitle' },
  },
]
