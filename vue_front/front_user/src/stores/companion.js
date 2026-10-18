import { defineStore } from 'pinia'
import { companionApi } from '@/api/companion'
export const useCompanionStore = defineStore('companion', {
  state: () => ({ usage: null, generation: 0 }),
  actions: {
    setUsage(value) {
      this.usage = value
    },
    clear() {
      this.generation++
      this.usage = null
    },
    async load() {
      const generation = this.generation
      const value = await companionApi.usage()
      if (generation === this.generation) this.setUsage(value)
    },
  },
})
