import { createRouter, createWebHistory } from 'vue-router'
import { routes } from './routes'
import { registerGuards } from './guards'
const router = createRouter({ history: createWebHistory(), routes })
registerGuards(router)
export default router
