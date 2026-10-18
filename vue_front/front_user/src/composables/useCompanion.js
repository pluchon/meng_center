import { computed, h, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  companionApi,
  sendCompanionMessage,
  regenerateCompanionMessage,
  companionImage,
} from '@/api/companion'
import { Companion, CompanionError, CompanionEvent } from '@/constants/meng'
import { companionArt } from '@/constants/companionArt'
import { RouteName } from '@/router/names'
import { useCompanionStore } from '@/stores/companion'
import { useUserStore } from '@/stores/user'
import { useImStore } from '@/stores/im'
import { useCompanionResource } from './useCompanionResource'
import { usePostTime } from './usePostTime'
import { notifyFailure } from './useFailureNotice'
import { ElMessage } from 'element-plus'
import { confirmDialog } from './useConfirm'

export function useCompanion() {
  const { t, tm } = useI18n(),
    router = useRouter(),
    route = useRoute()
  const usage = useCompanionStore(),
    user = useUserStore(),
    im = useImStore()
  const characters = useCompanionResource(
    (signal) => companionApi.characters({ signal, silent: true }),
    [],
  )
  const code = computed(() => String(route.params.character || '').toUpperCase())
  const chatting = computed(() => !!code.value)
  const character = computed(() => characters.data.value?.find((c) => c.code === code.value))
  const theme = computed(() => (character.value?.code || Companion.CHARACTERS[0]).toLowerCase())
  const avatar = (c) => companionArt[c]?.avatar
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
    sideReady = ref(false),
    sideError = ref(null),
    loadError = ref(null)
  const sending = ref(false),
    creating = ref(false),
    draft = ref(''),
    selectedWork = ref(null),
    feedbackBusy = ref(null),
    levelBusy = ref(false),
    attachments = ref([]),
    uploading = ref(false)
  const sendError = ref(null),
    statusText = ref(''),
    typing = ref(false),
    sideClosed = ref(false)
  const log = ref(null),
    dialog = ref(''),
    dialogCharacter = ref(''),
    dialogWorkId = ref(null),
    stageNotice = ref(null),
    hotlines = ref([])
  let generation = 0,
    sideRequest = 0,
    lastSideRefresh = 0,
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
  const envelopes = new Map()
  const sendControllers = new Set()
  let retryEnvelope = null,
    polling = false
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
      loading.value || !sessionId.value || usage.usage?.messages === 0 || cooldownSeconds.value > 0,
  )
  const stages = computed(() => tm('meng.stages'))
  const failedText = computed(() => sendError.value?.message || t('meng.failed'))
  const topicFull = computed(() => sessionTotal.value >= Companion.SESSION_LIMIT)
  const drawing = computed(
    () => !!sessionList.value.find((s) => String(s.id) === String(sessionId.value))?.drawing,
  )
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
    for (const controller of sendControllers) controller.abort()
    sendControllers.clear()
    sendController?.abort()
    sending.value = false
    typing.value = false
    statusText.value = ''
  }
  function saveLocalDraft() {
    if (sessionId.value)
      drafts.set(String(sessionId.value), {
        text: draft.value,
        images: attachments.value,
        work: selectedWork.value,
      })
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
    if (!current || !active) return
    const request = ++sideRequest
    sideLoading.value = true
    const results = await Promise.allSettled([
      companionApi.memories(current, { signal, silent: true }),
      companionApi.works({ character: current, page: 1, size: 3 }, { signal, silent: true }),
      usage.load(),
    ])
    if (token !== generation || request !== sideRequest || !active) return
    sideLoading.value = false
    sideError.value =
      results.slice(0, 2).find((r) => r.status === 'rejected' && !r.reason.cancelled)?.reason ||
      null
    if (results[0].status === 'fulfilled') memories.value = results[0].value
    if (results[1].status === 'fulfilled') recentWorks.value = results[1].value.records
    if (results[0].status === 'fulfilled' && results[1].status === 'fulfilled')
      sideReady.value = true
    lastSideRefresh = Date.now()
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
    selectedWork.value = saved?.work || null
    const topic = sessionList.value.find((s) => String(s.id) === String(id))
    if (topic) topic.unread = false
    typing.value = !!topic?.generating
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
    if (creating.value || topicFull.value) return
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
      {
        page,
        size: more ? Companion.PAGE_SIZE : Math.max(Companion.PAGE_SIZE, sessionList.value.length),
      },
      { signal: readController?.signal, silent: true },
    )
    if (token !== generation || !active) return
    sessionList.value = more ? sessionList.value.concat(result.records) : result.records
    sessionPage.value = more
      ? page
      : Math.max(1, Math.ceil(result.records.length / Companion.PAGE_SIZE))
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
    sideReady.value = false
    sideError.value = null
    lastSideRefresh = 0
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
  async function refreshCurrent(token = generation) {
    const id = sessionId.value
    if (!id) return
    const result = await companionApi.messages(
      id,
      { size: Companion.HISTORY_SIZE },
      { silent: true },
    )
    if (token !== generation || !active || String(id) !== String(sessionId.value)) return
    const records = result.records.toReversed(),
      oldest = records[0]?.id
    messages.value = [
      ...messages.value.filter((m) => typeof m.id === 'number' && m.id < oldest),
      ...records,
    ]
    before.value = before.value || result.nextBefore
    for (const record of records) {
      if (record.clientMessageId && record.turnState === 'COMMITTED')
        envelopes.delete(record.clientMessageId)
    }
    bottom()
  }
  async function receive(event, data, token, controller) {
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
            Math.max(Companion.TYPE_MIN_MS, (data.content?.length || 1) * Companion.TYPE_MS),
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
      recentWorks.value = [data, ...recentWorks.value.filter((w) => w.id !== data.id)].slice(0, 3)
      await appendMessage({
        id: 'work-' + data.id,
        role: 'CHARACTER',
        workId: data.id,
        content: data.title,
      })
    } else if (event === CompanionEvent.STAGE) {
      stageNotice.value = { ...data, previousStage: character.value?.stageName }
      if (data.message) await appendMessage(data.message)
    } else if (event === CompanionEvent.QUEUED) {
      const message = messages.value.find((m) => m.clientMessageId === data.clientMessageId)
      if (message) message.turnState = 'RECEIVED'
    } else if (event === CompanionEvent.DONE) usage.setUsage(data.usage)
    bottom()
  }
  async function send() {
    const text = draft.value.trim(),
      images = attachments.value.map((image) => image.id)
    if (inputDisabled.value || (!text && !images.length) || text.length > Companion.MAX_LENGTH)
      return
    const token = generation,
      id = sessionId.value,
      work = selectedWork.value
    const previous = retryEnvelope && envelopes.get(retryEnvelope)
    const body =
      previous &&
      previous.content === text &&
      JSON.stringify(previous.imageIds) === JSON.stringify(images)
        ? previous
        : {
            clientMessageId: crypto.randomUUID(),
            content: text,
            imageIds: images,
            workId: work?.id || null,
          }
    envelopes.set(body.clientMessageId, body)
    retryEnvelope = null
    const controller = new AbortController()
    sendController = controller
    sendControllers.add(controller)
    sending.value = true
    sendError.value = null
    typing.value = true
    await appendMessage({
      id: 'pending-' + body.clientMessageId,
      clientMessageId: body.clientMessageId,
      role: 'USER',
      content: text,
      imageIds: images,
      createTime: new Date().toISOString(),
      turnState: 'RECEIVED',
    })
    draft.value = ''
    attachments.value = []
    selectedWork.value = null
    try {
      await sendCompanionMessage(id, body, {
        signal: controller.signal,
        onEvent: (event, data) => receive(event, data, token, controller),
      })
    } catch (e) {
      if (token !== generation || controller.signal.aborted) return
      sendError.value = e
      if (e.code === CompanionError.COOLDOWN || e.code === CompanionError.QUOTA)
        void usage.load().catch(() => {})
      if (!draft.value) {
        draft.value = text
        attachments.value = images.map((imageId) => ({ id: imageId, url: companionImage(imageId) }))
        selectedWork.value = work
        retryEnvelope = body.clientMessageId
      }
    } finally {
      sendControllers.delete(controller)
      if (token === generation && !controller.signal.aborted) {
        sending.value = sendControllers.size > 0
        statusText.value = ''
        try {
          await refreshCurrent(token)
          await loadSessions()
          typing.value =
            sending.value ||
            !!sessionList.value.find((s) => String(s.id) === String(id))?.generating
          void loadSide(token)
          void refreshCharacters()
        } catch {
          /* 下一次轮询恢复已经提交的历史。 */
        }
      }
    }
  }
  function retryMessage(message) {
    draft.value = message.content
    attachments.value = (message.imageIds || []).map((id) => ({ id, url: companionImage(id) }))
    envelopes.set(message.clientMessageId, {
      clientMessageId: message.clientMessageId,
      content: message.content,
      imageIds: message.imageIds || [],
      workId: null,
    })
    retryEnvelope = message.clientMessageId
    void send()
  }
  async function regenerate(message) {
    if (feedbackBusy.value || sending.value) return
    const token = generation,
      controller = new AbortController()
    sendControllers.add(controller)
    feedbackBusy.value = message.id
    typing.value = true
    try {
      await regenerateCompanionMessage(message.id, {
        signal: controller.signal,
        onEvent: (event, data) => receive(event, data, token, controller),
      })
      if (token !== generation) return
      messages.value = []
      before.value = null
      await refreshCurrent(token)
      await loadSide(token)
    } catch (error) {
      if (!error.cancelled) notifyFailure(error)
    } finally {
      sendControllers.delete(controller)
      feedbackBusy.value = null
      if (token === generation) typing.value = false
    }
  }
  async function feedback(message) {
    if (feedbackBusy.value) return
    feedbackBusy.value = message.id
    try {
      await companionApi.feedback(message.id)
      ElMessage.success(t('meng.feedbackSaved'))
    } catch (error) {
      notifyFailure(error)
    } finally {
      feedbackBusy.value = null
    }
  }
  async function setLevel(level) {
    if (levelBusy.value || !character.value || level === character.value.level) return
    levelBusy.value = true
    const current = code.value
    try {
      await companionApi.level(current, level)
      await refreshCharacters()
    } catch (error) {
      notifyFailure(error)
    } finally {
      levelBusy.value = false
    }
  }
  async function revise(work) {
    if (code.value !== work.characterCode) {
      await goChat(work.characterCode)
      await start()
    }
    selectedWork.value = work
    dialog.value = ''
  }
  // 只有回复生成中或画图未完成时才短轮询；其余靠页面重新可见和消息域推送的萌萌通知触发刷新。
  const busy = () => sessionList.value.some((s) => s.generating || s.drawing)
  async function poll(force = false) {
    if (!active || !user.loggedIn || !chatting.value || document.hidden || polling || loading.value)
      return
    if (!force && !busy()) return
    polling = true
    const token = generation
    try {
      await loadSessions()
      if (token === generation && !sendControllers.size) {
        await refreshCurrent(token)
        typing.value = !!sessionList.value.find((s) => String(s.id) === String(sessionId.value))
          ?.generating
        if (Date.now() - lastSideRefresh >= Companion.SIDE_REFRESH_MS && !sideLoading.value)
          await loadSide(token)
      }
    } catch {
      /* 保留当前画面，后续轮询继续恢复。 */
    } finally {
      polling = false
    }
  }
  const pollTimer = setInterval(() => void poll(), Companion.POLL_MS)
  const visible = () => {
    if (!document.hidden) void poll(true)
  }
  document.addEventListener('visibilitychange', visible)
  const stopNoticeWatch = watch(
    () => im.revision,
    () => void poll(true),
  )
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
    // 萌萌里需要确认的都是删除和重置，统一用公共确认框的粉色危险样式
    const confirmed = await confirmDialog({
      message: text,
      title: t('meng.title'),
      confirmText: t('meng.confirm'),
      cancelText: t('meng.cancel'),
      danger: true,
    })
    if (!confirmed) return
    try {
      await action()
    } catch (e) {
      notifyFailure(e)
    }
  }
  function deleteTopic(id) {
    let forget = false
    const message = h('div', [
      h('p', t('meng.deleteConfirm')),
      h('label', [
        h('input', {
          type: 'checkbox',
          onChange: (event) => {
            forget = event.target.checked
          },
        }),
        t('meng.forgetSession'),
      ]),
    ])
    void confirm(async () => {
      await companionApi.deleteSession(id, forget)
      drafts.delete(String(id))
      if (String(id) === String(sessionId.value)) {
        await router.replace({ name: RouteName.MENG, params: route.params })
        await start()
      } else await loadSessions()
    }, message)
  }
  function openDialog(kind, workId = null, characterCode = code.value || Companion.CHARACTERS[0]) {
    dialogWorkId.value = workId
    dialogCharacter.value = characterCode
    dialog.value = kind
  }
  // 弹窗的角色独立于聊天路由，首页可以直接查看每位角色的关系。
  const modalCode = computed(
    () =>
      (stageNotice.value ? code.value : dialogCharacter.value) ||
      code.value ||
      Companion.CHARACTERS[0],
  )
  function openBond(c) {
    openDialog('bond', null, c)
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
      sideReady.value = false
      sideError.value = null
      dialog.value = ''
      dialogCharacter.value = ''
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
    clearInterval(pollTimer)
    document.removeEventListener('visibilitychange', visible)
    stopNoticeWatch()
    envelopes.clear()
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
    sideReady,
    sideError,
    loadError,
    sending,
    creating,
    draft,
    attachments,
    selectedWork,
    feedbackBusy,
    levelBusy,
    uploading,
    sendError,
    statusText,
    typing,
    drawing,
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
    regenerate,
    feedback,
    setLevel,
    revise,
    retryMessage,
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
