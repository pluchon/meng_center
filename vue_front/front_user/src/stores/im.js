import { defineStore } from 'pinia'
import { markRaw } from 'vue'
import { imApi } from '@/api/im'
import { connectIm } from '@/api/imConnection'
import { Im, MessageKind } from '@/constants/im'
import { useUserStore } from '@/stores/user'
const emptyUnread = () => ({ chat: 0, comment: 0, like: 0, system: 0 })
let connectionRuntime = null
export const useImStore = defineStore('im', {
  state: () => ({
    userId: null,
    unread: emptyUnread(),
    connection: 'offline',
    hidden: false,
    conversations: [],
    conversationTotal: 0,
    conversationPage: 1,
    conversationsLoading: false,
    conversationsError: '',
    current: null,
    messages: [],
    hasMore: false,
    historyLoading: false,
    historyError: '',
    reading: false,
    opening: false,
    currentError: '',
    generation: 0,
    revision: 0,
  }),
  getters: {
    totalUnread: (state) => Object.values(state.unread).reduce((total, value) => total + value, 0),
  },
  actions: {
    start(id) {
      if (this.userId === id) return
      this.stop()
      if (!id) return
      this.userId = id
      const runtime = {
        connection: null,
        refresh: null,
        controller: null,
        listController: null,
        listVersion: 0,
        readPending: false,
      }
      connectionRuntime = runtime
      runtime.connection = connectIm({
        state: (value) => {
          this.connection = value
        },
        connected: () => this.resync(),
        frame: (value) => this.frame(value),
      })
      void this.resync()
    },
    stop() {
      const runtime = connectionRuntime
      runtime?.connection?.stop()
      runtime?.controller?.abort()
      runtime?.listController?.abort()
      clearTimeout(runtime?.refresh)
      connectionRuntime = null
      const generation = this.generation + 1
      this.$reset()
      this.generation = generation
    },
    async resync() {
      const user = this.userId
      if (!user) return
      const results = await Promise.allSettled([imApi.unread(), imApi.settings()])
      if (this.userId !== user) return
      if (results[0].status === 'fulfilled') this.unread = results[0].value
      if (results[1].status === 'fulfilled') this.hidden = results[1].value.hidden
      this.revision++
      if (this.reading || this.current) {
        await this.loadConversations()
        if (this.current && this.userId === user) await this.refreshCurrent()
      }
    },
    async loadConversations(more = false) {
      const user = this.userId
      if (!user || (more && this.conversationsLoading)) return
      const runtime = connectionRuntime
      if (!runtime) return
      runtime.listController?.abort()
      const controller = markRaw(new AbortController()),
        version = ++runtime.listVersion
      runtime.listController = controller
      const page = more ? this.conversationPage + 1 : 1
      this.conversationsLoading = true
      this.conversationsError = ''
      try {
        const data = await imApi.conversations(
          { page, size: Im.MESSAGE_SIZE },
          { silent: true, signal: controller.signal },
        )
        if (user !== this.userId || version !== runtime.listVersion) return
        this.conversations = more
          ? [
              ...this.conversations,
              ...data.records.filter(
                (item) => !this.conversations.some((old) => old.id === item.id),
              ),
            ]
          : data.records
        this.conversationTotal = data.total
        this.conversationPage = page
      } catch (error) {
        if (user === this.userId && version === runtime.listVersion && !error.cancelled)
          this.conversationsError = error.message
      } finally {
        if (user === this.userId && version === runtime.listVersion)
          this.conversationsLoading = false
      }
    },
    async open(peerId) {
      const runtime = connectionRuntime
      runtime?.controller?.abort()
      const controller = markRaw(new AbortController())
      if (runtime) runtime.controller = controller
      const token = ++this.generation
      this.current = null
      this.messages = []
      this.hasMore = false
      this.opening = true
      this.currentError = ''
      this.historyError = ''
      this.historyLoading = false
      runtime?.connection?.watch(peerId)
      try {
        const current = await imApi.peer(peerId, { signal: controller.signal, silent: true })
        const history = current.id
          ? await imApi.messages(
              current.id,
              { size: Im.MESSAGE_SIZE },
              { signal: controller.signal, silent: true },
            )
          : { records: [], hasMore: false }
        if (token !== this.generation) return
        this.current = current
        this.messages = history.records
        this.hasMore = history.hasMore
        void this.markRead()
      } catch (error) {
        if (token === this.generation && !error.cancelled) this.currentError = error.message
      } finally {
        if (token === this.generation) this.opening = false
      }
    },
    close() {
      ++this.generation
      connectionRuntime?.controller?.abort()
      connectionRuntime?.connection?.watch(null)
      this.current = null
      this.messages = []
      this.reading = false
      this.opening = false
      this.currentError = ''
      this.historyLoading = false
    },
    setReading(value) {
      this.reading = value
      connectionRuntime?.connection?.watch(value ? this.current?.peer.id : null)
      if (value) void this.markRead()
    },
    async refreshCurrent() {
      const token = this.generation
      const peer = this.current?.peer.id
      if (!peer) return
      try {
        const current = await imApi.peer(peer, { silent: true })
        const history = current.id
          ? await imApi.messages(current.id, { size: Im.MESSAGE_SIZE }, { silent: true })
          : { records: [], hasMore: false }
        if (token !== this.generation) return
        this.current = current
        const oldest = history.records[0]?.id
        this.messages = [
          ...this.messages.filter(
            (item) => item.pending || item.error || (oldest && item.id < oldest),
          ),
          ...history.records,
        ].filter(
          (item, index, rows) =>
            rows.findIndex(
              (other) => other.clientMsgId === item.clientMsgId && other.senderId === item.senderId,
            ) === index,
        )
        if (!this.messages.length) this.hasMore = history.hasMore
        void this.markRead()
      } catch (error) {
        if (token === this.generation) this.historyError = error.message
      }
    },
    async earlier() {
      if (!this.current?.id || !this.hasMore || this.historyLoading) return
      const token = this.generation
      this.historyLoading = true
      this.historyError = ''
      try {
        const data = await imApi.messages(
          this.current.id,
          { beforeId: this.messages.find((item) => item.id)?.id, size: Im.MESSAGE_SIZE },
          { silent: true },
        )
        if (token !== this.generation) return
        const ids = new Set(this.messages.map((item) => item.id))
        this.messages = [...data.records.filter((item) => !ids.has(item.id)), ...this.messages]
        this.hasMore = data.hasMore
      } catch (error) {
        if (token === this.generation) this.historyError = error.message
      } finally {
        if (token === this.generation) this.historyLoading = false
      }
    },
    merge(message) {
      const index = this.messages.findIndex(
        (item) => item.clientMsgId === message.clientMsgId && item.senderId === message.senderId,
      )
      if (index < 0) this.messages.push(message)
      else this.messages.splice(index, 1, message)
    },
    async send(content, images = [], retry = null) {
      const peerId = retry?.peerId || this.current?.peer.id
      if (!peerId) return false
      const pending = retry || {
        peerId,
        clientMsgId: crypto.randomUUID(),
        senderId: useUserStore().current.id,
        content: images.length ? null : content,
        imageIds: images.map((item) => item.id),
        images,
        type: images.length ? MessageKind.IMAGE : MessageKind.TEXT,
        createTime: new Date().toISOString(),
      }
      pending.pending = true
      pending.error = ''
      this.merge({ ...pending })
      try {
        const result = await imApi.send({
          peerId,
          clientMsgId: pending.clientMsgId,
          content: pending.content,
          imageIds: pending.imageIds,
        })
        if (this.current?.peer.id === peerId) {
          this.merge(result)
          await this.refreshCurrent()
        }
        void this.loadConversations()
        return true
      } catch (error) {
        if (this.current?.peer.id === peerId) {
          this.merge({ ...pending, pending: false, error: error.message, code: error.code })
          if (['IM_BLOCKED', 'IM_PEER_NOT_FOUND'].includes(error.code))
            this.current = { ...this.current, unavailable: true }
        }
        return false
      }
    },
    async markRead() {
      const runtime = connectionRuntime
      const current = this.current
      if (
        !runtime ||
        runtime.readPending ||
        !this.reading ||
        document.hidden ||
        !document.hasFocus() ||
        !current?.id
      )
        return
      const last = [...this.messages]
        .reverse()
        .find((item) => item.id && item.senderId !== this.userId)
      if (!last || last.id <= current.lastReadMessageId) return
      runtime.readPending = true
      let succeeded = false
      try {
        await imApi.read(current.id, last.id)
        succeeded = true
        if (this.current?.id === current.id)
          this.current = { ...this.current, lastReadMessageId: last.id, unreadCount: 0 }
      } catch {
        /* 重连与再次聚焦时重试已读 */
      } finally {
        runtime.readPending = false
        if (
          succeeded &&
          this.current?.id === current.id &&
          this.messages.some((item) => item.id > last.id && item.senderId !== this.userId)
        )
          void this.markRead()
      }
    },
    frame({ type, data }) {
      if (type === 'unread') this.unread = data
      else if (type === 'presence' && this.current?.peer.id === data.userId)
        this.current = { ...this.current, online: data.online }
      else if (type === 'dm.read' && this.current?.id === data.conversationId) {
        this.current = {
          ...this.current,
          [data.userId === this.userId ? 'lastReadMessageId' : 'peerReadMessageId']: data.messageId,
        }
      } else if (type === 'dm.message') {
        if (
          this.current &&
          (this.current.id === data.conversationId ||
            (!this.current.id && data.senderId === this.current.peer.id))
        )
          this.merge(data)
        const runtime = connectionRuntime
        clearTimeout(runtime?.refresh)
        if (runtime)
          runtime.refresh = setTimeout(() => {
            void this.loadConversations()
            void this.refreshCurrent()
          }, Im.REFRESH_DELAY)
        void this.markRead()
      } else if (type === 'notice.new') this.revision++
    },
    async setHidden(hidden) {
      this.hidden = (await imApi.setting(hidden)).hidden
    },
    async setBlocked(peer, blocked) {
      await (blocked ? imApi.block(peer) : imApi.unblock(peer))
      if (this.current?.peer.id === peer) this.current = { ...this.current, blocked }
      this.conversations = this.conversations.map((item) =>
        item.peer.id === peer ? { ...item, blocked } : item,
      )
    },
    async deleteConversation(id) {
      await imApi.delete(id)
      this.conversations = this.conversations.filter((item) => item.id !== id)
      if (this.current?.id === id) this.close()
      await this.loadConversations()
    },
  },
})
