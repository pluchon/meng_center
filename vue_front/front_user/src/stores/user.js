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
    // 只有拿到资料或确认未登录（401）才算恢复完成；服务暂不可用或断网时保持未恢复，下次导航再试
    async restore() {
      try {
        this.current = await userApi.me(true)
        this.current.avatar = avatarUrl(this.current.avatar)
        this.restored = true
      } catch (error) {
        this.clear()
        if (error.status !== 401) throw error
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
