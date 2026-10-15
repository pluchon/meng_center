import {
  navMengIcon,
  messagePhotoIcon,
  messageSendIcon,
  messageRefreshIcon,
  messageCloseIcon,
  messageTrashIcon,
  authTickIcon,
} from '@/assets/images'
import { computed, defineComponent, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { IconArrowBackUp } from '@tabler/icons-vue'
import { useImStore } from '@/stores/im'
import { useEditorUploads } from '@/composables/useEditorUploads'
import { confirmDialog } from '@/composables/useConfirm'
import { notifyFailure } from '@/composables/useFailureNotice'
import { imApi, imImage } from '@/api/im'
import { Im, MessageTab } from '@/constants/im'
import { RouteName } from '@/router/names'
import { commentSegments, emotionToken } from '@/constants/comments'
import CommentBody from '@/components/comment-body/CommentBody.vue'
import EmotionPanel from '@/components/emotion-panel/EmotionPanel.vue'
import MessageImages from '@/components/message-images/MessageImages.vue'
import MessageAvatar from '@/components/message-avatar/MessageAvatar.vue'
import PageState from '@/components/page-state/PageState.vue'
// 把一条消息拆成文字、表情和图片：附件只有一个时放在气泡里文字的下一行，多个时在气泡下面排成图集
const partsCache = new WeakMap()
function parts(item) {
  const cached = partsCache.get(item)
  if (cached && cached.content === item.content && cached.images === item.images)
    return cached.value
  const value = split(item)
  partsCache.set(item, { content: item.content, images: item.images, value })
  return value
}
function split(item) {
  const segments = commentSegments(item.content || '')
  const emotions = segments
    .filter((segment) => segment.image)
    .map((segment) => ({ name: segment.name, image: segment.image }))
  const text = segments
    .filter((segment) => !segment.image)
    .map((segment) => segment.text)
    .join('')
    .replace(/\s{2,}/g, ' ')
    .trim()
  // 表情和图片排在一起：一个时放在气泡里文字的下一行，多个时在气泡下面叠成图集
  const items = [
    ...emotions.map((emotion) => ({
      kind: 'emotion',
      name: emotion.name,
      url: emotion.image,
      thumb: emotion.image,
    })),
    ...(item.images || []).map((image) => ({
      kind: 'image',
      url: imImage(image.id),
      thumb: imImage(image.id, true),
    })),
  ]
  return { text, items }
}
export default defineComponent({
  name: 'MessageChat',
  components: {
    IconArrowBackUp,
    PageState,
    MessageAvatar,
    EmotionPanel,
    CommentBody,
    MessageImages,
  },
  setup() {
    const { t } = useI18n(),
      router = useRouter(),
      im = useImStore(),
      uploads = useEditorUploads(imApi.upload)
    const text = ref(''),
      selectedEmotions = ref([]),
      field = ref(null),
      input = ref(null),
      scroll = ref(null),
      emotions = ref(false),
      changing = ref(false),
      busy = ref(false),
      revoking = ref(0),
      clock = ref(performance.now())
    // 选中的表情和输入的文字合在一起发出去，字数一起算
    const content = computed(
      () => text.value + selectedEmotions.value.map((item) => emotionToken(item.name)).join(''),
    )
    const count = computed(() => Array.from(content.value).length)
    const remaining = computed(() =>
      im.current?.peerSentCount ? null : Math.max(0, 3 - (im.current?.sentCount || 0)),
    )
    const disabled = computed(() => im.current?.unavailable || remaining.value === 0)
    const blocked = computed(() =>
      Boolean(
        im.conversations.find((item) => item.peer.id === im.current?.peer.id)?.blocked ??
        im.current?.blocked,
      ),
    )
    const valid = computed(
      () =>
        !disabled.value &&
        !busy.value &&
        !uploads.blocked.value &&
        count.value <= Im.TEXT_MAX &&
        Boolean(content.value.trim() || uploads.images.value.length),
    )
    const rows = computed(() =>
      im.messages.map((item, index, all) => ({
        ...item,
        ...parts(item),
        timeLabel:
          !index ||
          Date.parse(item.createTime) - Date.parse(all[index - 1].createTime) > Im.TIME_GAP,
      })),
    )
    const timeFormatter = new Intl.DateTimeFormat('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      timeZone: 'Asia/Taipei',
    })
    const formatTime = (value) => timeFormatter.format(new Date(value))
    // 撤回按钮只在自己发出、还没过时限的消息上出现；到点后按钮自己消失
    const nextExpiry = computed(() =>
      im.messages.reduce(
        (earliest, item) =>
          item.senderId === im.userId && item.revokeAt > clock.value
            ? Math.min(earliest, item.revokeAt)
            : earliest,
        Infinity,
      ),
    )
    let expiryTimer
    const stopExpiry = watch(
      nextExpiry,
      (at) => {
        clearTimeout(expiryTimer)
        if (at !== Infinity)
          expiryTimer = setTimeout(
            () => {
              clock.value = performance.now()
            },
            Math.max(0, at - performance.now()) + 30,
          )
      },
      { immediate: true },
    )
    function canRevoke(item) {
      return (
        item.senderId === im.userId &&
        !item.pending &&
        !item.error &&
        !item.revoked &&
        item.revokeAt > Math.max(clock.value, performance.now())
      )
    }
    async function revoke(item) {
      if (revoking.value) return
      const confirmed = await confirmDialog({
        message: t('im.revokeConfirm'),
        title: t('im.revoke'),
        confirmText: t('im.revoke'),
        cancelText: t('im.cancel'),
      })
      if (!confirmed) return
      revoking.value = item.id
      try {
        await im.revoke(item)
      } catch (failure) {
        notifyFailure(failure)
      } finally {
        revoking.value = 0
      }
    }
    // 把撤回的消息放回输入框：文字接在已有内容后面，表情变回待发的表情
    function reedit(item) {
      const segments = commentSegments(item.content || '')
      text.value += segments
        .filter((segment) => !segment.image)
        .map((segment) => segment.text)
        .join('')
      selectedEmotions.value = [
        ...selectedEmotions.value,
        ...segments
          .filter((segment) => segment.image)
          .map((segment) => ({
            name: segment.name,
            image: segment.image,
            key: crypto.randomUUID(),
          })),
      ].slice(0, Im.EMOTION_MAX)
      void nextTick(() => {
        resize()
        field.value?.focus()
      })
    }
    // 图片上传的提示（太大、格式不对、张数超限）统一用顶部的消息弹框
    const stopNotice = watch(
      () => uploads.notice.value,
      (message) => {
        if (message) ElMessage.warning(message)
      },
      // 同样的提示连续出现也要每次都弹
      { flush: 'sync' },
    )
    function resize() {
      if (field.value) {
        field.value.style.height = 'auto'
        field.value.style.height = field.value.scrollHeight + 'px'
      }
    }
    async function bottom(smooth = false) {
      await nextTick()
      scroll.value?.scrollTo({
        top: scroll.value.scrollHeight,
        behavior:
          smooth && !matchMedia('(prefers-reduced-motion: reduce)').matches ? 'smooth' : 'instant',
      })
    }
    let disposed = false,
      resizeObserver,
      previousHeight = 0
    const stopMessages = watch(
      () => im.messages.length,
      (length, previous) => {
        if (!scroll.value || im.historyLoading || length === previous) return
        const nearBottom =
          scroll.value.scrollHeight - scroll.value.scrollTop - scroll.value.clientHeight < 160
        if (nearBottom || !previous || im.messages.at(-1)?.senderId === im.userId)
          void bottom(Boolean(previous))
      },
      { flush: 'post' },
    )
    watch(
      () => im.current?.id,
      () => {
        void bottom()
      },
    )
    async function earlier() {
      const host = scroll.value,
        height = host?.scrollHeight || 0,
        top = host?.scrollTop || 0
      await im.earlier()
      await nextTick()
      if (host && !disposed) host.scrollTop = top + host.scrollHeight - height
    }
    function scrollHistory() {
      if (scroll.value?.scrollTop < 40 && im.hasMore && !im.historyError) void earlier()
    }
    function insert(item) {
      if (selectedEmotions.value.length >= Im.EMOTION_MAX) {
        ElMessage.warning(t('im.emotionMax', { max: Im.EMOTION_MAX }))
        return
      }
      if (count.value + Array.from(emotionToken(item.name)).length > Im.TEXT_MAX) {
        ElMessage.warning(t('im.tooLong', { max: Im.TEXT_MAX, emotions: Im.EMOTION_MAX }))
        return
      }
      selectedEmotions.value.push({ ...item, key: crypto.randomUUID() })
      emotions.value = false
      void nextTick(() => field.value?.focus())
    }
    function removeEmotion(item) {
      selectedEmotions.value = selectedEmotions.value.filter((emotion) => emotion.key !== item.key)
    }
    async function send() {
      if (!valid.value) return
      const body = content.value,
        images = uploads.images.value.map((item) => ({
          id: item.id,
          width: item.width,
          height: item.height,
        }))
      busy.value = true
      text.value = ''
      selectedEmotions.value = []
      uploads.hydrate([], null)
      emotions.value = false
      resize()
      const promise = im.send(body, images)
      void bottom()
      try {
        await promise
      } finally {
        busy.value = false
      }
    }
    function keydown(event) {
      if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
        event.preventDefault()
        void send()
      }
    }
    function picked(event) {
      uploads.add(event.target.files)
      event.target.value = ''
    }
    function paste(event) {
      if (event.clipboardData?.files.length && !disabled.value) {
        event.preventDefault()
        uploads.add(event.clipboardData.files)
      }
    }
    // 屏蔽对方：直接勾选，失败就把勾还回去
    async function toggleBlock(event) {
      if (changing.value || !im.current) return
      const target = event.target.checked
      changing.value = true
      try {
        await im.setBlocked(im.current.peer.id, target)
      } catch (failure) {
        event.target.checked = !target
        notifyFailure(failure)
      } finally {
        changing.value = false
      }
    }
    async function removeChat() {
      if (changing.value || !im.current?.id) return
      const confirmed = await confirmDialog({
        message: t('im.removeConfirm'),
        title: t('im.remove'),
        confirmText: t('im.remove'),
        cancelText: t('im.cancel'),
        danger: true,
      })
      if (!confirmed) return
      changing.value = true
      try {
        await im.deleteConversation(im.current.id)
        if (!im.current)
          await router.replace({ name: RouteName.MESSAGES, query: { tab: MessageTab.CHAT } })
      } catch (failure) {
        notifyFailure(failure)
      } finally {
        changing.value = false
      }
    }
    const focus = () => {
      void im.markRead()
    }
    onMounted(() => {
      void bottom()
      resizeObserver = new ResizeObserver(() => {
        const host = scroll.value
        if (!host) return
        if (!previousHeight || host.scrollHeight - host.scrollTop - previousHeight < 160)
          void bottom()
        previousHeight = host.clientHeight
      })
      if (scroll.value) resizeObserver.observe(scroll.value)
      window.addEventListener('focus', focus)
      document.addEventListener('visibilitychange', focus)
    })
    onUnmounted(() => {
      disposed = true
      resizeObserver?.disconnect()
      stopMessages()
      stopNotice()
      stopExpiry()
      clearTimeout(expiryTimer)
      window.removeEventListener('focus', focus)
      document.removeEventListener('visibilitychange', focus)
    })
    return {
      navMengIcon,
      messagePhotoIcon,
      messageSendIcon,
      messageRefreshIcon,
      messageCloseIcon,
      messageTrashIcon,
      tickIcon: authTickIcon,
      t,
      im,
      Im,
      text,
      selectedEmotions,
      field,
      input,
      scroll,
      emotions,
      busy,
      revoking,
      changing,
      count,
      remaining,
      disabled,
      blocked,
      valid,
      rows,
      formatTime,
      canRevoke,
      revoke,
      reedit,
      resize,
      earlier,
      scrollHistory,
      insert,
      removeEmotion,
      send,
      keydown,
      picked,
      paste,
      toggleBlock,
      removeChat,
      uploads,
    }
  },
})
