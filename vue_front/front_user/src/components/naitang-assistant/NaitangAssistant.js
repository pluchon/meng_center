import { computed, defineComponent, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import {
  IconX,
  IconPlus,
  IconHistory,
  IconArrowLeft,
  IconTrash,
  IconPhoto,
  IconSend,
  IconMoodSmile,
  IconRefresh,
  IconWifiOff,
} from '@tabler/icons-vue'
import { companionApi, companionImage, companionVoice, sendCompanionMessage } from '@/api/companion'
import { communitySparkle, companionNekoAvatar, companionOfflineVoice } from '@/assets/images'
import { commentSegments, emotionToken } from '@/constants/comments'
import EmotionPanel from '@/components/emotion-panel/EmotionPanel.vue'
import MessageImages from '@/components/message-images/MessageImages.vue'
import NaitangPet from './NaitangPet.vue'
import NaitangVoice from './NaitangVoice.vue'
import { flightFrames } from './flightPath'
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
    IconArrowLeft,
    IconTrash,
    IconPhoto,
    IconSend,
    IconMoodSmile,
    IconRefresh,
    IconWifiOff,
    EmotionPanel,
    MessageImages,
    NaitangPet,
    NaitangVoice,
  },
  setup() {
    const { t } = useI18n(),
      user = useUserStore(),
      emotions = useEmotionStore()
    const router = useRouter(),
      route = useRoute()
    const opened = ref(false),
      listing = ref(false),
      memoryCleared = ref(false),
      loading = ref(false)
    const creating = ref(false),
      typing = ref(false),
      failure = ref(null),
      offline = ref(false),
      loadError = ref(null)
    const sessions = ref([]),
      total = ref(0),
      sessionPage = ref(1),
      selected = ref(null)
    const messages = ref([]),
      before = ref(null),
      input = ref(''),
      attachments = ref([]),
      picked = ref([]),
      emotionOpen = ref(false),
      naitangOnline = ref(true)
    const usage = ref({ messages: null, cooldownSeconds: 0 }),
      status = ref(''),
      hotlines = ref([])
    const log = ref(null),
      editor = ref(null),
      picker = ref(null),
      sendButton = ref(null),
      plane = ref(null),
      sending = ref(0)
    const voicing = ref(null),
      longBubbles = ref(new Set()),
      petCheers = ref(0)
    const controllers = new Set()
    let timer,
      voice = null,
      disposed = false,
      historyVersion = 0,
      polling = false
    // 用户自己断网：请求根本没有到达服务端；奶糖离线：服务端在，但奶糖那边不可用
    const isUserNetwork = (error) => Boolean(error) && !error.status && error.code === 'NETWORK'
    const isNaitangDown = (error) =>
      Boolean(error) && (error.status >= 500 || error.code === CompanionError.UNAVAILABLE)
    // 奶糖掉线：灰点亮起，等一个探测间隔后再去看她回来没有
    let lastProbe = 0
    function markDown() {
      naitangOnline.value = false
      lastProbe = Date.now()
    }
    // 出错时分开处理：断网只亮标题旁的标签，奶糖离线只换头像上的灰点，其余的才在输入框上方报错
    function report(error) {
      if (isUserNetwork(error)) offline.value = true
      else {
        if (isNaitangDown(error)) markDown()
        failure.value = error
      }
    }
    const blocked = computed(() => usage.value.messages === 0 || usage.value.cooldownSeconds > 0)
    const mediaFull = computed(
      () => attachments.value.length + picked.value.length >= Companion.MAX_MEDIA,
    )
    // 发出去的正文：文字后面跟着选中的表情记号
    const composed = () =>
      input.value.trim() + picked.value.map((item) => emotionToken(item.name)).join('')
    const uploading = computed(() => attachments.value.some((image) => !image.id))
    const canSend = computed(
      () =>
        selected.value &&
        !blocked.value &&
        !uploading.value &&
        (input.value.trim() || attachments.value.length || picked.value.length) &&
        composed().length <= Companion.MAX_LENGTH,
    )
    const atLimit = computed(() => total.value >= Companion.SESSION_LIMIT)
    const thinking = computed(
      () =>
        typing.value || sessions.value.some((item) => item.id === selected.value && item.generating),
    )
    const petState = computed(() => {
      if (offline.value || !naitangOnline.value) return 'offline'
      if (typing.value || voicing.value !== null || (!opened.value && sending.value)) return 'talk'
      if (thinking.value || sending.value) return 'think'
      return 'idle'
    })
    // 自己发的消息里，表情记号拆出来和图片排在一起，气泡里只留文字
    function ownParts(content) {
      const segments = commentSegments(content || '')
      return {
        text: segments
          .filter((segment) => !segment.image)
          .map((segment) => segment.text)
          .join('')
          .trim(),
        emotions: segments.filter((segment) => segment.image),
      }
    }
    const bubbleText = (message) =>
      message.role === 'USER' ? ownParts(message.content).text : message.content
    const gallery = (message) => [
      ...(message.role === 'USER' ? ownParts(message.content).emotions : []).map((segment) => ({
        kind: 'emotion',
        name: segment.name,
        url: segment.image,
        thumb: segment.image,
      })),
      ...(message.imageIds || []).map((id) => ({
        kind: 'image',
        url: companionImage(id),
        thumb: companionImage(id),
        name: t('companion.image'),
      })),
    ]
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
    // 语音先摆在气泡右侧量行数，超过三行就移进气泡；移进去只会更长，不会来回跳
    function measureBubble(element, message) {
      if (!element || !message.voiceMs || longBubbles.value.has(message.id)) return
      const style = getComputedStyle(element)
      const height = element.clientHeight - parseFloat(style.paddingTop) - parseFloat(style.paddingBottom)
      if (Math.round(height / parseFloat(style.lineHeight)) > Companion.VOICE_OUTSIDE_LINES)
        longBubbles.value.add(message.id)
    }
    function stopVoice() {
      voice?.pause()
      voice = null
      voicing.value = null
    }
    // 同一时间只放一条，再点一次停止
    function toggleVoice(message) {
      const same = voicing.value === message.id
      stopVoice()
      if (same) return
      const current = new Audio(message.local ? companionOfflineVoice : companionVoice(message.id))
      const failed = () => {
        if (voice !== current) return
        stopVoice()
        failure.value = { message: t('companion.voiceFailed') }
      }
      voice = current
      voicing.value = message.id
      current.onended = () => voice === current && stopVoice()
      current.onerror = failed
      current.play().catch(failed)
    }
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
    // 输入框随内容长高，上限交给样式的 max-height，超出后在框内滚动
    function fitEditor() {
      const element = editor.value
      if (!element) return
      element.style.height = 'auto'
      element.style.height = element.scrollHeight + element.offsetHeight - element.clientHeight + 'px'
    }
    // 纸飞机从发送按钮沿弧线飞到奶糖思考中那一行的头像上，落地时头像轻轻弹一下
    function flyPlane() {
      const node = plane.value,
        avatar = log.value?.querySelector('.naitang-thinking .naitang-avatar')
      if (!node || !avatar || !sendButton.value) return
      if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
      const from = sendButton.value.getBoundingClientRect(),
        to = avatar.getBoundingClientRect()
      const flight = node.animate(
        flightFrames(
          { x: from.left + from.width / 2, y: from.top + from.height / 2 },
          { x: to.left + to.width / 2, y: to.top + to.height / 2 },
          Companion.PLANE_ARC,
        ),
        { duration: Companion.PLANE_MS, easing: 'cubic-bezier(0.4, 0.1, 0.3, 1)' },
      )
      flight.onfinish = () =>
        avatar.animate(
          [
            { transform: 'scale(1)' },
            { transform: 'scale(1.22) rotate(-6deg)' },
            { transform: 'scale(0.96) rotate(3deg)' },
            { transform: 'scale(1)' },
          ],
          { duration: Companion.LANDING_MS, easing: 'ease-out' },
        )
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
        stopVoice()
        historyVersion++
        loading.value = false
        messages.value = []
        before.value = null
        failure.value = null
        offline.value = false
        status.value = ''
        hotlines.value = []
        memoryCleared.value = false
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
        report(error)
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
      offline.value = !navigator.onLine
      try {
        await Promise.all([
          listSessions(),
          emotions.load(),
          companionApi.usage().then((value) => {
            usage.value = value
            naitangOnline.value = true
          }),
        ])
        if (!selected.value || !sessions.value.some((session) => session.id === selected.value)) {
          if (sessions.value.length) await choose(sessions.value[0].id)
          else await newSession()
        } else if (!sending.value) await history()
        await nextTick()
        editor.value?.focus()
      } catch (error) {
        if (isUserNetwork(error)) offline.value = true
        else if (isNaitangDown(error)) markDown()
        loadError.value = error
      }
    }
    function close() {
      stopVoice()
      opened.value = false
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
        report(error)
      }
    }
    async function clearMemories() {
      if (!(await confirm('companion.clearConfirm', 'companion.clearTitle'))) return
      try {
        await companionApi.clearMemories()
        memoryCleared.value = true
        await bottom(true)
      } catch (error) {
        report(error)
      }
    }
    function addEmotion(item) {
      emotionOpen.value = false
      if (mediaFull.value) {
        failure.value = { message: t('companion.maxMedia', { max: Companion.MAX_MEDIA }) }
        return
      }
      if ((composed() + emotionToken(item.name)).length > Companion.MAX_LENGTH) return
      picked.value = [...picked.value, { ...item, key: crypto.randomUUID() }]
      void nextTick(() => editor.value?.focus())
    }
    function removeEmotion(emotion) {
      picked.value = picked.value.filter((item) => item.key !== emotion.key)
    }
    function removeAttachment(image) {
      attachments.value = attachments.value.filter((item) => item.preview !== image.preview)
      URL.revokeObjectURL(image.preview)
    }
    async function addFiles(files) {
      if (blocked.value || !selected.value) return
      for (const file of files) {
        if (mediaFull.value) {
          failure.value = { message: t('companion.maxMedia', { max: Companion.MAX_MEDIA }) }
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
          report(error)
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
        composed() === body.content &&
        attachments.value.length === body.imageIds.length &&
        attachments.value.every((image, index) => image.id === body.imageIds[index])
      )
    }
    function clearInput() {
      input.value = ''
      picked.value = []
      attachments.value.forEach((image) => URL.revokeObjectURL(image.preview))
      attachments.value = []
    }
    function pause(ms, signal) {
      return new Promise((resolve, reject) => {
        const timer = setTimeout(resolve, ms)
        signal.addEventListener(
          'abort',
          () => {
            clearTimeout(timer)
            reject({ cancelled: true })
          },
          { once: true },
        )
      })
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
      memoryCleared.value = false
      const session = sessions.value.find((item) => item.id === id)
      if (session) session.generating = true
      bottom(true).then(flyPlane)
      let replied = false,
        completed = false
      const onEvent = async (event, data) => {
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
            if (!messages.value.some((message) => message.id === data.id)) {
              messages.value.push(data)
              replied ||= data.role === 'CHARACTER'
            }
            typing.value = false
            await bottom()
          }
        }
        if (event === CompanionEvent.DONE) usage.value = data.usage
      }
      try {
        // 断网或断流时用同一个 clientMessageId 重发：服务端按它去重，已生成的回复会重放，不会多算一条
        for (let attempt = 0; ; attempt++) {
          try {
            await sendCompanionMessage(id, body, { signal: controller.signal, onEvent })
            if (selected.value === id) offline.value = false
            naitangOnline.value = true
            completed = true
            break
          } catch (error) {
            const delay = Companion.RETRY_DELAYS_MS[attempt]
            if (isUserNetwork(error) && selected.value === id) offline.value = true
            if (error.cancelled || !isUserNetwork(error) || delay === undefined || disposed)
              throw error
            await pause(delay, controller.signal)
          }
        }
      } catch (error) {
        if (!disposed && !error.cancelled && selected.value === id) {
          optimistic.failed = true
          if (isNaitangDown(error)) {
            // 奶糖离线：头像变灰点，她用事先录好的一句话回一下；消息左边留着重发的小图标
            markDown()
            messages.value.push({
              id: 'offline-' + crypto.randomUUID(),
              role: 'CHARACTER',
              kind: 'TEXT',
              content: t('companion.offlineReply'),
              voiceMs: Companion.OFFLINE_VOICE_MS,
              local: true,
            })
          } else if (!isUserNetwork(error)) {
            // 断网只在聊天区显示「网络异常」那一行和标题旁的标签，网络恢复后自动重发
            failure.value = error
            // 不覆盖请求期间新输入的内容；附件用已鉴权的上传地址恢复，避免保留临时对象 URL。
            if (!input.value && !attachments.value.length && !picked.value.length) {
              const parts = ownParts(body.content)
              input.value = parts.text
              picked.value = parts.emotions.map((segment) => ({
                name: segment.name,
                image: segment.image,
                key: crypto.randomUUID(),
              }))
              attachments.value = body.imageIds.map((imageId) => ({
                id: imageId,
                preview: companionImage(imageId),
              }))
            }
          }
          if (error.code === CompanionError.QUOTA) usage.value.messages = 0
          if (error.code === CompanionError.COOLDOWN)
            usage.value = await companionApi.usage().catch(() => usage.value)
        }
      } finally {
        controllers.delete(controller)
        sending.value--
        optimistic.pending = false
        const current = sessions.value.find((item) => item.id === id)
        if (current) current.generating = false
        if (!disposed && selected.value === id) {
          typing.value = false
          if (!sending.value && !optimistic.failed) {
            status.value = ''
            if (completed && replied && Math.random() < 0.2) petCheers.value++
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
        content: composed(),
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
    // 奶糖离线时每隔一会儿探一下，回来了就把灰点换回绿点
    async function probe() {
      if (!opened.value || naitangOnline.value || offline.value) return
      if (Date.now() - lastProbe < Companion.PRESENCE_PROBE_MS) return
      lastProbe = Date.now()
      try {
        usage.value = await companionApi.usage()
        naitangOnline.value = true
      } catch (error) {
        if (isUserNetwork(error)) offline.value = true
      }
    }
    async function poll() {
      void probe()
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
    watch([input, listing, opened], fitEditor, { flush: 'post' })
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
    // 网络回来：去掉标签，断网前没发出去的那条自动重发；打开时就没连上的，重新加载一遍
    function reconnect() {
      const wasOffline = offline.value
      offline.value = false
      if (!wasOffline || sending.value) return
      if (opened.value && loadError.value && isUserNetwork(loadError.value)) {
        void open()
        return
      }
      const failed = messages.value.findLast((message) => message.failed && message.retryBody)
      if (failed) transmit(selected.value, failed.retryBody, failed)
    }
    const disconnect = () => {
      offline.value = true
    }
    onMounted(() => {
      timer = setInterval(poll, Companion.POLL_MS)
      window.addEventListener('online', reconnect)
      window.addEventListener('offline', disconnect)
    })
    onUnmounted(() => {
      disposed = true
      window.removeEventListener('online', reconnect)
      window.removeEventListener('offline', disconnect)
      stopVoice()
      clearInterval(timer)
      controllers.forEach((controller) => controller.abort())
      attachments.value.forEach((image) => URL.revokeObjectURL(image.preview))
    })
    return {
      t,
      Companion,
      RouteName,
      companionNekoAvatar,
      sparkleMask: { maskImage: 'url(' + JSON.stringify(communitySparkle) + ')' },
      gallery,
      bubbleText,
      isUserNetwork,
      picked,
      emotionOpen,
      naitangOnline,
      petState,
      petCheers,
      mediaFull,
      addEmotion,
      removeEmotion,
      safeHelp,
      segments,
      sticker,
      voicing,
      longBubbles,
      measureBubble,
      toggleVoice,
      opened,
      listing,
      memoryCleared,
      thinking,
      loading,
      creating,
      sending,
      failure,
      offline,
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
      sendButton,
      plane,
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
