import { defineStore } from 'pinia'
import { userApi, avatarUrl } from '@/api/user'
export const useUserStore = defineStore('user', {
  state: () => ({ current: null, restored: false }),
  getters: { loggedIn: (state) => !!state.current },
  actions: {
    setCurrent(value) {
      this.current = value ? { ...value, avatar: avatarUrl(value.avatar) } : null
    },
    clear() {
      this.current = null
    },
    async restore() {
      try {
        this.current = await userApi.me(true)
        this.current.avatar = avatarUrl(this.current.avatar)
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
      this.current.avatar = avatarUrl(this.current.avatar)
    },
    async logout() {
      await userApi.logout()
      this.clear()
    },
  },
})
