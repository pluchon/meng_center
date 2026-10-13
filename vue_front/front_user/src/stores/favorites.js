import { defineStore } from 'pinia'
import { contentApi } from '@/api/content'
import { useUserStore } from './user'
import { Account } from '@/constants/account'
let settle = null
export const useFavoriteStore = defineStore('favorites', {
  state: () => ({ folders: [], owner: null, open: false }),
  actions: {
    invalidate() {
      this.folders = []
      this.finish(null)
    },
    finish(id) {
      this.open = false
      if (settle) {
        settle(id)
        settle = null
      }
    },
    async choose() {
      const owner = useUserStore().current?.id
      if (!owner) return null
      if (this.owner !== owner) {
        this.invalidate()
        this.owner = owner
      }
      if (!this.folders.length) {
        const value = await contentApi.folders({ page: 1, size: Account.FOLDERS_MAX })
        if (useUserStore().current?.id !== owner) return null
        this.folders = value.records
      }
      if (this.folders.length === 1) return this.folders[0].id
      this.finish(null)
      this.open = true
      return new Promise((resolve) => {
        settle = resolve
      })
    },
  },
})
