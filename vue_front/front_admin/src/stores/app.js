import { defineStore } from 'pinia'
import { Theme, StorageKey } from '@/constants/app'
export const useAppStore = defineStore('app', {
  state: () => ({ theme: Theme.LIGHT, language: 'zh-CN' }),
  actions: {
    initialize() {
      this.setTheme(
        localStorage.getItem(StorageKey.THEME) === Theme.DARK ? Theme.DARK : Theme.LIGHT,
      )
    },
    setTheme(theme) {
      this.theme = theme
      localStorage.setItem(StorageKey.THEME, theme)
      document.documentElement.classList.toggle(Theme.DARK, theme === Theme.DARK)
    },
  },
})
