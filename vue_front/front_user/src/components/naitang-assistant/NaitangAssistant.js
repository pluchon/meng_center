import { computed, defineComponent, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import {
  IconX,
  IconPlus,
  IconHistory,
  IconDots,
  IconArrowLeft,
  IconTrash,
  IconPhoto,
  IconSend,
} from '@tabler/icons-vue'
import { companionApi, companionImage, sendCompanionMessage } from '@/api/companion'
import { companionNekoAvatar } from '@/assets/images'
import { Companion, CompanionError, CompanionEvent } from '@/constants/companion'
import { RouteName } from '@/router/names'
import { useUserStore } from '@/stores/user'
import { useEmotionStore } from '@/stores/emotions'
import { confirmDialog } from '@/composables/useConfirm'

export default defineComponent({
  name: 'NaitangAssistant',
  components: {
    IconX,
    IconPlus,
    IconHistory,
    IconDots,
    IconArrowLeft,
    IconTrash,
    IconPhoto,
    IconSend,
  },
  setup() {
    const { t } = useI18n(),
      user = useUserStore(),
      emotions = useEmotionStore()
    const router = useRouter(),
      route = useRoute()
    const opened = ref(false),
      listing = ref(false),
      menu = ref(false),
      loading = ref(false)
    const creating = ref(false),
      typing = ref(false),
      failure = ref(null),
      loadError = ref(null)
    const sessions = ref([]),
      total = ref(0),
      sessionPage = ref(1),
      selected = ref(null)
    const messages = ref([]),
      before = ref(null),
      input = ref(''),
      attachments = ref([])
    const usage = ref({ messages: null, cooldownSeconds: 0 }),
      status = ref(''),
      hotlines = ref([])
    const log = ref(null),
      editor = ref(null),
      picker = ref(null),
      sending = ref(0)
    const controllers = new Set()
    let timer,
      disposed = false,
      historyVersion = 0,
      polling = false
    const blocked = computed(() => usage.value.messages === 0 || usage.value.cooldownSeconds > 0)
    const uploading = computed(() => attachments.value.some((image) => !image.id))
    const canSend = computed(
      () =>
        selected.value &&
        !blocked.value &&
        !uploading.value &&
        (input.value.trim() || attachments.value.length) &&
        input.value.length <= Companion.MAX_LENGTH,
    )
    const atLimit = computed(() => total.value >= Companion.SESSION_LIMIT)
    const safeHelp = (line) =>
      /^https:\/\//.test(line.url || '')
        ? line.url
        : /^[-+\d ()]+$/.test(line.phone || '')
          ? 'tel:' + line.phone
          : undefined
    const segments = (value) =>
      (value || '')
        .split(/(（[^（）]*）)/u)
        .filter(Boolean)
        .map((text) => ({ text, action: text.startsWith('（') && text.endsWith('）') }))
    const sticker = (name) =>
      emotions.items.find((item) => item.name === name && item.series === 'cirno')?.image
    function follow() {
      return (
        !log.value ||
        log.value.scrollHeight - log.value.scrollTop - log.value.clientHeight <
          Companion.SCROLL_THRESHOLD
      )
    }
    async function bottom(force = false) {
      const should = force || follow()
      await nextTick()
      if (should && log.value) log.value.scrollTop = log.value.scrollHeight
    }
    async function listSessions(append = false) {
      const page = append ? sessionPage.value + 1 : 1
      const result = await companionApi.sessions({ page, size: Companion.PAGE_SIZE })
      if (disposed) return
      sessions.value = append ? [...sessions.value, ...result.records] : result.records
      total.value = result.total
      sessionPage.value = page
    }
    async function history(older = false) {
      if (!selected.value || loading.value) return
      const id = selected.value,
        version = ++historyVersion
      const oldHeight = log.value?.scrollHeight || 0,
        oldTop = log.value?.scrollTop || 0
      const shouldFollow = follow()
      loading.value = true
      loadError.value = null
      try {
        const result = await companionApi.messages(
          id,
          { before: older ? before.value : undefined, size: Companion.HISTORY_SIZE },
          { silent: true },
        )
        if (disposed || version !== historyVersion || id !== selected.value) return
        const records = [...result.records].reverse()
        messages.value = older ? [...records, ...messages.value] : records
        before.value = result.nextBefore
        await nextTick()
        if (log.value)
          log.value.scrollTop = older
            ? oldTop + log.value.scrollHeight - oldHeight
            : shouldFollow
              ? log.value.scrollHeight
              : oldTop
      } catch (error) {
        if (!disposed && id === selected.value) loadError.value = error
      } finally {
        if (version === historyVersion) loading.value = false
      }
    }
    async function choose(id) {
      if (id !== selected.value) {
        historyVersion++
        loading.value = false
        messages.value = []
        before.value = null
        failure.value = null
        status.value = ''
        hotlines.value = []
      }
      typing.value = false
      selected.value = id
      listing.value = false
      await history()
      await bottom(true)
    }
    async function newSession() {
      if (creating.value || atLimit.value) return
      creating.value = true
      failure.value = null
      try {
        const session = await companionApi.createSession()
        await listSessions()
        await choose(session.id)
      } catch (error) {
        failure.value = error
      } finally {
        creating.value = false
      }
    }
    async function open() {
      if (!user.loggedIn) {
        router.push({ name: RouteName.LOGIN, query: { redirect: route.fullPath } })
        return
      }
      opened.value = true
      loadError.value = null
      try {
        await Promise.all([
          listSessions(),
          emotions.load(),
          companionApi.usage().then((value) => {
            usage.value = value
          }),
        ])
        if (!selected.value || !sessions.value.some((session) => session.id === selected.value)) {
          if (sessions.value.length) await choose(sessions.value[0].id)
          else await newSession()
        } else if (!sending.value) await history()
        await nextTick()
        editor.value?.focus()
      } catch (error) {
        loadError.value = error
      }
    }
    function close() {
      opened.value = false
      menu.value = false
    }
    async function confirm(message, title) {
      return confirmDialog({
        message: t(message),
        title: t(title),
        confirmText: t('companion.confirm'),
        cancelText: t('companion.cancel'),
        danger: true,
      })
    }
    async function removeSession(id) {
      if (!(await confirm('companion.deleteConfirm', 'companion.deleteSession'))) return
      try {
        await companionApi.deleteSession(id)
        await listSessions()
        if (selected.value === id) {
          selected.value = null
          messages.value = []
          if (sessions.value.length) await choose(sessions.value[0].id)
        }
      } catch (error) {
        failure.value = error
      }
    }
    async function clearMemories() {
      menu.value = false
      if (!(await confirm('companion.clearConfirm', 'companion.clearTitle'))) return
      try {
        await companionApi.clearMemories()
      } catch (error) {
        failure.value = error
      }
    }
    function removeAttachment(image) {
      attachments.value = attachments.value.filter((item) => item.preview !== image.preview)
      URL.revokeObjectURL(image.preview)
    }
    async function addFiles(files) {
      if (blocked.value || !selected.value) return
      for (const file of files) {
        if (attachments.value.length >= Companion.MAX_IMAGES) {
          failure.value = { message: t('companion.maxImages') }
          break
        }
        if (!file.type.startsWith('image/') || file.size > Companion.IMAGE_BYTES) {
          failure.value = { message: t('companion.invalidImage') }
          continue
        }
        const image = { preview: URL.createObjectURL(file), id: null }
        attachments.value.push(image)
        try {
          image.id = (await companionApi.upload(file)).id
          attachments.value = [...attachments.value]
        } catch (error) {
          removeAttachment(image)
          failure.value = error
        }
      }
    }
    function pick(event) {
      addFiles([...event.target.files])
      event.target.value = ''
    }
    function paste(event) {
      const files = [...(event.clipboardData?.files || [])]
      if (files.length) {
        event.preventDefault()
        addFiles(files)
      }
    }
    function drop(event) {
      if (!blocked.value) addFiles([...event.dataTransfer.files])
    }
    function matchesInput(body) {
      return (
        input.value.trim() === body.content &&
        attachments.value.length === body.imageIds.length &&
        attachments.value.every((image, index) => image.id === body.imageIds[index])
      )
    }
    function clearInput() {
      input.value = ''
      attachments.value.forEach((image) => URL.revokeObjectURL(image.preview))
      attachments.value = []
    }
    async function transmit(id, body, optimistic) {
      if (optimistic.pending) return
      if (selected.value === id && matchesInput(body)) clearInput()
      const controller = new AbortController()
      controllers.add(controller)
      sending.value++
      optimistic.pending = true
      optimistic.failed = false
      failure.value = null
      const session = sessions.value.find((item) => item.id === id)
      if (session) session.generating = true
      try {
        await sendCompanionMessage(id, body, {
          signal: controller.signal,
          onEvent: async (event, data) => {
            if (disposed || selected.value !== id) return
            if (event === CompanionEvent.STATUS) {
              if (data.kind === 'CRISIS_HELP') hotlines.value = data.hotlines || []
              else status.value = data.text || ''
            }
            if (event === CompanionEvent.QUEUED) status.value = t('companion.queued')
            if (
              event === CompanionEvent.BUBBLE &&
              !messages.value.some((message) => message.id === data.id)
            ) {
              typing.value = true
              await bottom()
              const delay = Math.min(
                Companion.TYPE_MAX_MS,
                Math.max(Companion.TYPE_MIN_MS, (data.content || '').length * Companion.TYPE_MS),
              )
              await new Promise((resolve) => setTimeout(resolve, delay))
              if (!disposed && selected.value === id) {
                if (!messages.value.some((message) => message.id === data.id))
                  messages.value.push(data)
                typing.value = false
                await bottom()
              }
            }
            if (event === CompanionEvent.DONE) usage.value = data.usage
          },
        })
      } catch (error) {
        if (!disposed && !error.cancelled && selected.value === id) {
          optimistic.failed = true
          failure.value = error
          // 不覆盖请求期间新输入的内容；附件用已鉴权的上传地址恢复，避免保留临时对象 URL。
          if (!input.value && !attachments.value.length) {
            input.value = body.content
            attachments.value = body.imageIds.map((imageId) => ({
              id: imageId,
              preview: companionImage(imageId),
            }))
          }
          if (error.code === CompanionError.QUOTA) usage.value.messages = 0
          if (error.code === CompanionError.COOLDOWN)
            usage.value = await companionApi.usage().catch(() => usage.value)
        }
      } finally {
        controllers.delete(controller)
        sending.value--
        optimistic.pending = false
        if (!disposed && selected.value === id) {
          typing.value = false
          if (!sending.value && !optimistic.failed) {
            status.value = ''
            await Promise.all([listSessions(), history()]).catch((error) => {
              loadError.value = error
            })
          }
        }
      }
    }
    async function send() {
      if (!canSend.value) return
      const failed = messages.value.findLast(
        (message) => message.failed && message.retryBody && matchesInput(message.retryBody),
      )
      if (failed) return transmit(selected.value, failed.retryBody, failed)
      const body = {
        clientMessageId: crypto.randomUUID(),
        content: input.value.trim(),
        imageIds: attachments.value.map((image) => image.id),
      }
      const message = {
        id: body.clientMessageId,
        clientMessageId: body.clientMessageId,
        role: 'USER',
        kind: 'TEXT',
        content: body.content,
        imageIds: body.imageIds,
        turnState: 'RECEIVED',
        retryBody: body,
      }
      messages.value.push(message)
      clearInput()
      await bottom(true)
      await transmit(selected.value, body, messages.value.at(-1))
    }
    async function poll() {
      if (usage.value.cooldownSeconds > 0)
        usage.value.cooldownSeconds = Math.max(
          0,
          usage.value.cooldownSeconds - Companion.POLL_MS / 1000,
        )
      const current = sessions.value.find((session) => session.id === selected.value)
      if (!opened.value || !user.loggedIn || sending.value || !current?.generating || polling)
        return
      polling = true
      try {
        await listSessions()
        await history()
        usage.value = await companionApi.usage()
      } catch (error) {
        loadError.value = error
      } finally {
        polling = false
      }
    }
    watch(
      () => user.loggedIn,
      (loggedIn) => {
        if (!loggedIn) {
          close()
          selected.value = null
          messages.value = []
          sessions.value = []
          input.value = ''
          attachments.value.forEach((image) => URL.revokeObjectURL(image.preview))
          attachments.value = []
          hotlines.value = []
          controllers.forEach((controller) => controller.abort())
        }
      },
    )
    onMounted(() => {
      timer = setInterval(poll, Companion.POLL_MS)
    })
    onUnmounted(() => {
      disposed = true
      clearInterval(timer)
      controllers.forEach((controller) => controller.abort())
      attachments.value.forEach((image) => URL.revokeObjectURL(image.preview))
    })
    return {
      t,
      Companion,
      RouteName,
      companionNekoAvatar,
      companionImage,
      safeHelp,
      segments,
      sticker,
      opened,
      listing,
      menu,
      loading,
      creating,
      typing,
      sending,
      failure,
      loadError,
      sessions,
      total,
      selected,
      messages,
      before,
      input,
      attachments,
      usage,
      status,
      hotlines,
      log,
      editor,
      picker,
      blocked,
      uploading,
      canSend,
      atLimit,
      open,
      close,
      choose,
      newSession,
      removeSession,
      clearMemories,
      removeAttachment,
      pick,
      paste,
      drop,
      send,
      retry: (message) =>
        transmit(
          selected.value,
          message.retryBody || {
            clientMessageId: message.clientMessageId,
            content: message.content,
            imageIds: message.imageIds || [],
          },
          message,
        ),
      history,
      listSessions,
    }
  },
})
