import { defineStore } from 'pinia'
import { contentApi } from '@/api/content'
import { notifyFailure } from '@/composables/useFailureNotice'
import { useUserStore } from './user'
import { Account } from '@/constants/account'
let settle = null
let applyResult = null
let requestId = 0
export const useFavoriteStore = defineStore('favorites', {
  state: () => ({
    records: [],
    total: 0,
    page: 1,
    postId: null,
    // 当前勾选的收藏夹，和上次保存的结果比较得出有没有改动
    selected: [],
    saved: [],
    saving: false,
    loading: false,
    error: null,
    owner: null,
    open: false,
  }),
  getters: {
    dirty: (state) =>
      state.selected.length !== state.saved.length ||
      state.selected.some((id) => !state.saved.includes(id)),
  },
  actions: {
    invalidate() {
      this.records = []
      this.close()
    },
    close() {
      this.open = false
      applyResult = null
      if (settle) {
        settle()
        settle = null
      }
    },
    // 面板里翻页：分页由后端负责，每次只取当前页；勾选状态留在本地，翻页不丢
    async turn(page) {
      const id = ++requestId
      this.loading = true
      this.error = null
      try {
        const value = await contentApi.folders({ page, size: Account.PICKER_SIZE })
        if (id !== requestId) return
        this.records = value.records
        this.total = value.total
        this.page = page
      } catch (e) {
        if (id === requestId) this.error = e
      } finally {
        if (id === requestId) this.loading = false
      }
    },
    // 勾选或取消，只改本地；已经选满时返回 false
    toggle(folderId) {
      if (this.selected.includes(folderId)) {
        this.selected = this.selected.filter((id) => id !== folderId)
        return true
      }
      if (this.selected.length >= Account.POST_FOLDERS_MAX) return false
      this.selected = [...this.selected, folderId]
      return true
    },
    // 一次性保存勾选结果，成功后同步当前页的篇数和帖子的收藏状态
    async save() {
      if (this.saving || !this.dirty) return
      this.saving = true
      try {
        const result = await contentApi.savePostFolders(this.postId, this.selected)
        for (const folder of this.records) {
          const was = this.saved.includes(folder.id),
            now = this.selected.includes(folder.id)
          if (now !== was) folder.count += now ? 1 : -1
        }
        this.saved = [...this.selected]
        applyResult?.(result)
      } catch (e) {
        notifyFailure(e)
      } finally {
        this.saving = false
      }
    },
    // 管理一篇帖子的收藏：只有一个收藏夹且帖子还没收藏时直接放进去，其余打开面板，面板关闭后返回
    async manage(post, apply) {
      const owner = useUserStore().current?.id
      if (!owner) return
      if (this.owner !== owner) {
        this.invalidate()
        this.owner = owner
      }
      // 每次都重新取第一页和这篇帖子所在的收藏夹，数量、篇数和勾选状态以最新为准
      const [first, selected] = await Promise.all([
        contentApi.folders({ page: 1, size: Account.PICKER_SIZE }),
        contentApi.postFolderIds(post.id),
      ])
      if (useUserStore().current?.id !== owner) return
      if (!post.favorited && first.total === 1) {
        apply(await contentApi.addToFolder(first.records[0].id, post.id))
        return
      }
      this.close()
      requestId++
      this.postId = post.id
      this.records = first.records
      this.total = first.total
      this.page = 1
      this.selected = [...selected]
      this.saved = [...selected]
      this.saving = false
      this.loading = false
      this.error = null
      applyResult = apply
      this.open = true
      return new Promise((resolve) => {
        settle = resolve
      })
    },
  },
})
