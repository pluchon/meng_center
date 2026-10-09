import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/global.scss'
import './styles/element.scss'
import App from './App.vue'
import { i18n } from './locales'
import router from './router'
import { useUserStore } from './stores/user'
import { onUnauthorized } from './api/request'
import { RouteName } from './router/names'
const app = createApp(App)
app.use(createPinia()).use(i18n).use(ElementPlus).use(router)
onUnauthorized(async () => {
  useUserStore().clear()
  if (router.currentRoute.value.name !== RouteName.LOGIN)
    await router.replace({
      name: RouteName.LOGIN,
      query: { redirect: router.currentRoute.value.fullPath },
    })
})
app.mount('#app')
