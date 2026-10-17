import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessageBox } from 'element-plus'
import { companionApi, sendCompanionMessage, companionImage } from '@/api/companion'
import { Companion, CompanionError, CompanionEvent } from '@/constants/meng'
import { companionArt } from '@/constants/companionArt'
import { RouteName } from '@/router/names'
import { useCompanionStore } from '@/stores/companion'
import { useUserStore } from '@/stores/user'
import { useCompanionResource } from './useCompanionResource'
import { usePostTime } from './usePostTime'
import { notifyFailure } from './useFailureNotice'

export function useCompanion() {
  const { t, tm } = useI18n(),
    router = useRouter(),
    route = useRoute()
  const usage = useCompanionStore(),
    user = useUserStore()
  const characters = useCompanionResource(
    (signal) => companionApi.characters({ signal, silent: true }),
    [],
  )
  const code = computed(() => String(route.params.character || '').toUpperCase())
  const chatting = computed(() => !!code.value)
  const character = computed(() => characters.data.value?.find((c) => c.code === code.value))
  const theme = computed(() => (character.value?.code || Companion.CHARACTERS[0]).toLowerCase())
  const avatar = (c) => companionArt[c]?.avatar
  const portrait = (c, stage = 0, error = null) => {
    const art = companionArt[c]
    if (!art) return ''
    if (error?.code === CompanionError.COOLDOWN || (c === code.value && cooldownSeconds.value > 0))
      return art.angry
    if (error) return art.worried
    return art.portraits[stage >= 4 ? 2 : stage >= 2 ? 1 : 0]
  }
  const sessionList = ref([]),
    sessionPage = ref(1),
    sessionTotal = ref(0),
    sessionId = ref(null)
  const messages = ref([]),
    before = ref(null),
    memories = ref([]),
    recentWorks = ref([])
  const loading = ref(false),
    historyLoading = ref(false),
    sideLoading = ref(false),
    sideError = ref(null),
    loadError = ref(null)
  const sending = ref(false),
    creating = ref(false),
    draft = ref(''),
    attachments = ref([]),
    uploading = ref(false)
  const sendError = ref(null),
    statusText = ref(''),
    typing = ref(false),
    sideClosed = ref(false)
  const log = ref(null),
    dialog = ref(''),
    dialogWorkId = ref(null),
    stageNotice = ref(null),
    hotlines = ref([])
  let generation = 0,
    readController,
    sendController,
    active = true,
    scrollFrame
  const cooldownSeconds = ref(0)
  let cooldownTimer,
    cooldownUntil = 0
  const stopCooldownWatch = watch(
    () => usage.usage?.cooldownSeconds,
    (seconds) => {
      clearInterval(cooldownTimer)
      cooldownUntil = Date.now() + Math.max(0, seconds || 0) * 1000
      const tick = () => {
        cooldownSeconds.value = Math.max(0, Math.ceil((cooldownUntil - Date.now()) / 1000))
        if (!cooldownSeconds.value) {
          clearInterval(cooldownTimer)
          if (sendError.value?.code === CompanionError.COOLDOWN) sendError.value = null
        }
      }
      tick()
      if (cooldownSeconds.value) cooldownTimer = setInterval(tick, 1000)
    },
    { immediate: true },
  )
  const cooldownText = computed(
    () =>
      String(Math.floor(cooldownSeconds.value / 60)).padStart(2, '0') +
      ':' +
      String(cooldownSeconds.value % 60).padStart(2, '0'),
  )
  const drafts = new Map()
  const progress = (c) =>
    Math.min(100, ((c?.stage || 1) - 1 + Math.min(1, Math.max(0, c?.progress || 0))) * 25)
  const localProgress = (c) => Math.min(100, Math.max(0, (c?.progress || 0) * 100))
  const relativeTime = usePostTime()
  const timeFormatter = new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Taipei',
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
  const dayFormatter = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Taipei' })
  const timestamp = (value) => new Date(/(Z|[+-]\d\d:\d\d)$/.test(value) ? value : value + 'Z')
  const date = (value) => (value ? timeFormatter.format(timestamp(value)) : '')
  const day = (value) => (value ? dayFormatter.format(timestamp(value)) : '')
  const inputDisabled = computed(
    () =>
      sending.value ||
      loading.value ||
      !sessionId.value ||
      usage.usage?.messages === 0 ||
      cooldownSeconds.value > 0,
  )
  const stages = computed(() => tm('meng.stages'))
  const failedText = computed(() => sendError.value?.message || t('meng.failed'))
  const topicFull = computed(() => sessionTotal.value >= Companion.SESSION_LIMIT)
  function bottom(force = false) {
    const element = log.value
    if (!force && element && element.scrollHeight - element.clientHeight - element.scrollTop > 120)
      return
    cancelAnimationFrame(scrollFrame)
    void nextTick(() => {
      scrollFrame = requestAnimationFrame(() => {
        if (log.value) log.value.scrollTop = log.value.scrollHeight
      })
    })
  }
  function stopRound() {
    sendController?.abort()
    sending.value = false
    typing.value = false
    statusText.value = ''
  }
  function saveLocalDraft() {
    if (sessionId.value)
      drafts.set(String(sessionId.value), { text: draft.value, images: attachments.value })
  }
  async function goHome() {
    await router.push({ name: RouteName.MENG })
  }
  async function goChat(c) {
    await router.push({ name: RouteName.MENG, params: { character: c.toLowerCase() } })
  }
  async function refreshCharacters() {
    await characters.execute()
  }
  async function loadSide(token = generation, signal = readController?.signal) {
    const current = code.value
    sideLoading.value = true
    const results = await Promise.allSettled([
      companionApi.memories(current, { signal, silent: true }),
      companionApi.works({ character: current, page: 1, size: 3 }, { signal, silent: true }),
      usage.load(),
    ])
    if (token !== generation || !active) return
    sideLoading.value = false
    sideError.value =
      results.find((r) => r.status === 'rejected' && !r.reason.cancelled)?.reason || null
    if (results[0].status === 'fulfilled') memories.value = results[0].value
    if (results[1].status === 'fulfilled') recentWorks.value = results[1].value.records
  }
  async function selectSession(id) {
    const scope = generation
    while (
      !sessionList.value.some((s) => String(s.id) === String(id)) &&
      sessionList.value.length < sessionTotal.value
    ) {
      try {
        await loadSessions(true)
      } catch (e) {
        if (!e.cancelled) loadError.value = e
        return
      }
      if (scope !== generation) return
    }
    if (!sessionList.value.some((s) => String(s.id) === String(id))) {
      loadError.value = { status: 404, code: 'COMPANION_SESSION_NOT_FOUND' }
      loading.value = false
      return
    }
    if (String(id) === String(sessionId.value)) return
    saveLocalDraft()
    stopRound()
    readController?.abort()
    const token = ++generation
    readController = new AbortController()
    sessionId.value = id
    messages.value = []
    before.value = null
    sendError.value = null
    hotlines.value = []
    const saved = drafts.get(String(id))
    draft.value = saved?.text || ''
    attachments.value = saved?.images || []
    loading.value = true
    historyLoading.value = false
    loadError.value = null
    void router.replace({ name: RouteName.MENG, params: route.params, query: { s: String(id) } })
    try {
      const result = await companionApi.messages(
        id,
        { size: Companion.HISTORY_SIZE },
        { signal: readController.signal, silent: true },
      )
      if (token !== generation) return
      messages.value = result.records.toReversed()
      before.value = result.nextBefore
      bottom(true)
    } catch (e) {
      if (token === generation && !e.cancelled) loadError.value = e
    } finally {
      if (token === generation) loading.value = false
    }
    if (token === generation) void loadSide(token)
  }
  async function newTopic() {
    if (creating.value || sending.value || topicFull.value) return
    creating.value = true
    const current = code.value
    try {
      const session = await companionApi.createSession(current)
      if (current !== code.value || !active) return
      sessionList.value.unshift(session)
      sessionTotal.value++
      await selectSession(session.id)
    } catch (e) {
      sendError.value = e
    } finally {
      creating.value = false
    }
  }
  async function loadSessions(more = false) {
    const token = generation,
      current = code.value,
      page = more ? sessionPage.value + 1 : 1
    const result = await companionApi.sessions(
      current,
      { page, size: Companion.PAGE_SIZE },
      { signal: readController?.signal, silent: true },
    )
    if (token !== generation || !active) return
    sessionList.value = more ? sessionList.value.concat(result.records) : result.records
    sessionPage.value = page
    sessionTotal.value = result.total
  }
  async function loadEarlier() {
    if (!before.value || historyLoading.value) return
    const token = generation,
      height = log.value?.scrollHeight || 0,
      top = log.value?.scrollTop || 0
    historyLoading.value = true
    try {
      const result = await companionApi.messages(
        sessionId.value,
        { before: before.value, size: Companion.HISTORY_SIZE },
        { signal: readController.signal, silent: true },
      )
      if (token !== generation) return
      const known = new Set(messages.value.map((m) => String(m.id)))
      messages.value.unshift(...result.records.toReversed().filter((m) => !known.has(String(m.id))))
      before.value = result.nextBefore
      await nextTick()
      if (log.value) log.value.scrollTop = top + log.value.scrollHeight - height
    } catch (e) {
      notifyFailure(e)
    } finally {
      if (token === generation) historyLoading.value = false
    }
  }
  async function start() {
    saveLocalDraft()
    stopRound()
    readController?.abort()
    const token = ++generation
    readController = new AbortController()
    sessionId.value = null
    sessionList.value = []
    memories.value = []
    recentWorks.value = []
    sideLoading.value = false
    loadError.value = null
    sendError.value = null
    loading.value = true
    await refreshCharacters()
    if (token !== generation) return
    if (!chatting.value) {
      loading.value = false
      void usage.load().catch(() => {})
      return
    }
    if (!character.value) {
      loading.value = false
      return
    }
    try {
      await loadSessions()
      if (token !== generation) return
      const requested = route.query.s
      if (requested && !/^\d+$/.test(String(requested)))
        throw { code: 'COMPANION_SESSION_NOT_FOUND' }
      const chosen = requested || sessionList.value[0]?.id
      if (chosen) await selectSession(chosen)
      else {
        loading.value = false
        void loadSide(token)
      }
    } catch (e) {
      if (token === generation && !e.cancelled) loadError.value = e
      loading.value = false
    }
  }
  async function appendMessage(message) {
    const known = messages.value.findIndex((m) => String(m.id) === String(message.id))
    if (known >= 0) messages.value[known] = message
    else messages.value.push(message)
    bottom()
  }
  async function send() {
    const text = draft.value.trim()
    if (
      inputDisabled.value ||
      (!text && !attachments.value.length) ||
      text.length > Companion.MAX_LENGTH
    )
      return
    const token = generation,
      id = sessionId.value,
      images = attachments.value.map((image) => image.id)
    sending.value = true
    sendError.value = null
    typing.value = true
    sendController = new AbortController()
    const controller = sendController
    const temporary = {
      id: 'pending-' + Date.now(),
      role: 'USER',
      content: text,
      imageIds: images,
      createTime: new Date().toISOString(),
    }
    await appendMessage(temporary)
    draft.value = ''
    attachments.value = []
    let complete = false
    try {
      await sendCompanionMessage(
        id,
        { content: text, imageIds: images },
        {
          signal: controller.signal,
          onEvent: async (event, data) => {
            if (token !== generation || controller.signal.aborted) return
            if (event === CompanionEvent.STATUS) {
              if (data.kind === 'CRISIS_HELP') hotlines.value = data.hotlines || []
              else statusText.value = data.text || ''
            } else if (event === CompanionEvent.BUBBLE) {
              statusText.value = ''
              typing.value = true
              await new Promise((resolve) => {
                const timer = setTimeout(
                  done,
                  Math.min(
                    Companion.TYPE_MAX_MS,
                    Math.max(Companion.TYPE_MIN_MS, data.content.length * Companion.TYPE_MS),
                  ),
                )
                function done() {
                  clearTimeout(timer)
                  controller.signal.removeEventListener('abort', done)
                  resolve()
                }
                controller.signal.addEventListener('abort', done, { once: true })
              })
              if (token === generation && !controller.signal.aborted) await appendMessage(data)
            } else if (event === CompanionEvent.WORK) {
              recentWorks.value = [
                data,
                ...recentWorks.value.filter((w) => w.id !== data.id),
              ].slice(0, 3)
              await appendMessage({
                id: 'work-' + data.id,
                role: 'CHARACTER',
                workId: data.id,
                content: data.title,
              })
            } else if (event === CompanionEvent.STAGE) {
              stageNotice.value = { ...data, previousStage: character.value?.stageName }
              if (data.message) await appendMessage(data.message)
            } else if (event === CompanionEvent.DONE) {
              complete = true
              usage.setUsage(data.usage)
            }
            bottom()
          },
        },
      )
    } catch (e) {
      if (token !== generation || controller.signal.aborted) return
      sendError.value = e
      // 次数用完和冷却以服务端用量为准，刷新后输入区随之置灰
      if (e.code === CompanionError.COOLDOWN || e.code === CompanionError.QUOTA)
        void usage.load().catch(() => {})
      draft.value ||= text
      attachments.value = images.map((imageId) => ({ id: imageId, url: companionImage(imageId) }))
    } finally {
      if (token === generation && !controller.signal.aborted) {
        sending.value = false
        typing.value = false
        statusText.value = ''
        // 以持久化历史替换临时消息，避免作品事件和历史记录重复。
        try {
          const result = await companionApi.messages(
            id,
            { size: Companion.HISTORY_SIZE },
            { silent: true, signal: readController.signal },
          )
          if (token === generation) {
            const records = result.records.toReversed(),
              oldest = records[0]?.id
            messages.value = [
              ...messages.value.filter((m) => typeof m.id === 'number' && m.id < oldest),
              ...records,
            ]
            before.value = before.value || result.nextBefore
            bottom()
          }
        } catch {
          /* 保留已经显示的消息，下一次进入时恢复历史。 */
        }
        if (complete) {
          void loadSessions().catch(() => {})
          void loadSide(token)
          void refreshCharacters()
        }
      }
    }
  }
  async function addImages(files) {
    if (uploading.value || inputDisabled.value) return
    uploading.value = true
    const token = generation
    try {
      for (const file of Array.from(files)
        .filter((f) => f.type.startsWith('image/'))
        .slice(0, Companion.MAX_IMAGES - attachments.value.length)) {
        const result = await companionApi.upload(file)
        if (token !== generation) return
        attachments.value.push({ id: result.id, url: companionImage(result.id) })
      }
    } catch (e) {
      sendError.value = e
    } finally {
      uploading.value = false
    }
  }
  function pasteImages(event) {
    if (event.clipboardData?.files.length) {
      event.preventDefault()
      void addImages(event.clipboardData.files)
    }
  }
  function dropImages(event) {
    event.preventDefault()
    void addImages(event.dataTransfer.files)
  }
  function keydown(event) {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault()
      void send()
    }
  }
  async function confirm(action, text = t('meng.deleteConfirm')) {
    try {
      await ElMessageBox.confirm(text, t('meng.title'), {
        confirmButtonText: t('meng.confirm'),
        cancelButtonText: t('meng.cancel'),
      })
    } catch {
      return
    }
    try {
      await action()
    } catch (e) {
      notifyFailure(e)
    }
  }
  function deleteTopic(id) {
    void confirm(async () => {
      await companionApi.deleteSession(id)
      drafts.delete(String(id))
      if (String(id) === String(sessionId.value)) {
        await router.replace({ name: RouteName.MENG, params: route.params })
        await start()
      } else await loadSessions()
    })
  }
  function openDialog(kind, workId = null) {
    dialogWorkId.value = workId
    dialog.value = kind
  }
  // 首页没有当前角色时（作品柜）弹窗用第一个角色的主题色
  const modalCode = computed(() => code.value || Companion.CHARACTERS[0])
  // 首页卡片的「我们的关系」先进入这个角色的聊天，再在聊天上打开关系弹窗
  async function openBond(c) {
    if (c !== code.value) await goChat(c)
    openDialog('bond')
  }
  const stopUserWatch = watch(
    () => user.current?.id,
    () => {
      ++generation
      readController?.abort()
      stopRound()
      characters.clear()
      messages.value = []
      memories.value = []
      recentWorks.value = []
      usage.clear()
      drafts.clear()
    },
  )
  watch(code, start, { immediate: true })
  watch(
    () => route.query.s,
    (id) => {
      if (id && String(id) !== String(sessionId.value) && !loading.value) void selectSession(id)
    },
  )
  onBeforeUnmount(() => {
    active = false
    ++generation
    readController?.abort()
    stopRound()
    cancelAnimationFrame(scrollFrame)
    drafts.clear()
    stopUserWatch()
    stopCooldownWatch()
    clearInterval(cooldownTimer)
  })
  return reactive({
    t,
    tm,
    Companion,
    RouteName,
    characters,
    code,
    chatting,
    character,
    theme,
    avatar,
    portrait,
    stages,
    progress,
    localProgress,
    date,
    day,
    relativeTime,
    usage,
    sessionList,
    sessionTotal,
    sessionId,
    messages,
    memories,
    recentWorks,
    loading,
    historyLoading,
    sideLoading,
    sideError,
    loadError,
    sending,
    creating,
    draft,
    attachments,
    uploading,
    sendError,
    statusText,
    typing,
    sideClosed,
    log,
    before,
    failedText,
    topicFull,
    cooldownSeconds,
    cooldownText,
    inputDisabled,
    dialog,
    dialogWorkId,
    modalCode,
    stageNotice,
    hotlines,
    companionImage,
    goHome,
    goChat,
    refreshCharacters,
    selectSession,
    newTopic,
    loadSessions,
    loadEarlier,
    start,
    send,
    pasteImages,
    dropImages,
    keydown,
    confirm,
    deleteTopic,
    openDialog,
    openBond,
    loadSide,
  })
}
