import { defineStore } from 'pinia'
import { userApi } from '@/api/user'
export const useUserStore = defineStore('user', {
  state: () => ({ current: null, restored: false }),
  getters: { loggedIn: (state) => !!state.current },
  actions: {
    clear() {
      this.current = null
    },
    async restore() {
      try {
        this.current = await userApi.me(true)
      } catch (error) {
        this.clear()
        if (error.status !== 401) throw error
      } finally {
        this.restored = true
      }
    },
    async login(data) {
      await userApi.login(data)
      this.current = await userApi.me()
    },
    async logout() {
      await userApi.logout()
      this.clear()
    },
  },
})
