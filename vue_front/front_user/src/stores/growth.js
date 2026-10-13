import { defineStore } from 'pinia'
import { growthApi } from '@/api/growth'
export const useGrowthStore = defineStore('growth', {
  state: () => ({ data: null, loading: false, sending: false, error: null, generation: 0 }),
  getters: {
    progress: (state) => {
      const d = state.data
      if (!d) return 0
      if (d.nextLevelExp == null) return 100
      return Math.max(
        0,
        Math.min(100, ((d.exp - d.levelStartExp) / (d.nextLevelExp - d.levelStartExp)) * 100),
      )
    },
  },
  actions: {
    clear() {
      this.generation++
      this.data = null
      this.error = null
      this.loading = false
      this.sending = false
    },
    async load() {
      if (this.loading || this.sending) return
      const token = this.generation
      this.loading = true
      this.error = null
      try {
        const data = await growthApi.me()
        if (token === this.generation) this.data = data
      } catch (e) {
        if (token === this.generation) this.error = e
      } finally {
        if (token === this.generation) this.loading = false
      }
    },
    async checkIn() {
      if (this.sending || this.data?.checkedIn) return null
      const token = this.generation
      this.sending = true
      try {
        const data = await growthApi.checkIn()
        if (token !== this.generation) return null
        this.data = data
        this.error = null
        return data
      } catch (e) {
        if (token !== this.generation) return null
        if (e.status === 409) {
          this.sending = false
          await this.load()
        }
        throw e
      } finally {
        if (token === this.generation) this.sending = false
      }
    },
  },
})
