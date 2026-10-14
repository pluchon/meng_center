import {
  navMengIcon,
  messagePhotoIcon,
  messageSendIcon,
  messageRefreshIcon,
  messageCloseIcon,
} from '@/assets/images'
import { computed, defineComponent, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useImStore } from '@/stores/im'
import { useEditorUploads } from '@/composables/useEditorUploads'
import { imApi } from '@/api/im'
import { Im, MessageKind } from '@/constants/im'
import { Emotions, emotionToken } from '@/constants/comments'
import CommentBody from '@/components/comment-body/CommentBody.vue'
import MessageImages from '@/components/message-images/MessageImages.vue'
import MessageAvatar from '@/components/message-avatar/MessageAvatar.vue'
export default defineComponent({
  name: 'MessageChat',
  components: {
    MessageAvatar,

    CommentBody,
    MessageImages,
  },
  setup() {
    const { t } = useI18n(),
      im = useImStore(),
      uploads = useEditorUploads(imApi.upload)
    const text = ref(''),
      error = ref(''),
      field = ref(null),
      input = ref(null),
      scroll = ref(null),
      emotions = ref(false),
      busy = ref(false)
    const count = computed(() => Array.from(text.value).length)
    const remaining = computed(() =>
      im.current?.peerSentCount ? null : Math.max(0, 3 - (im.current?.sentCount || 0)),
    )
    const disabled = computed(() => im.current?.unavailable || remaining.value === 0)
    const valid = computed(
      () =>
        !disabled.value &&
        !busy.value &&
        !uploads.blocked.value &&
        count.value <= Im.TEXT_MAX &&
        Boolean(text.value.trim() || uploads.images.value.length),
    )
    const rows = computed(() =>
      im.messages.map((item, index, all) => ({
        ...item,
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
      const total = (text.value.match(/\[表情:[^\s[\]]{1,12}\]/gu) || []).length
      if (total >= Im.EMOTION_MAX) {
        error.value = t('im.tooLong', { max: Im.TEXT_MAX, emotions: Im.EMOTION_MAX })
        return
      }
      const token = emotionToken(item.name),
        start = field.value?.selectionStart ?? text.value.length,
        end = field.value?.selectionEnd ?? start
      text.value = text.value.slice(0, start) + token + text.value.slice(end)
      emotions.value = false
      void nextTick(() => {
        field.value?.focus()
        field.value?.setSelectionRange(start + token.length, start + token.length)
        resize()
      })
    }
    async function send() {
      if (!valid.value) return
      error.value = ''
      if (text.value.trim() && uploads.images.value.length) {
        error.value = t('im.mixed')
        return
      }
      if ((text.value.match(/\[表情:[^\s[\]]{1,12}\]/gu) || []).length > Im.EMOTION_MAX) {
        error.value = t('im.tooLong', { max: Im.TEXT_MAX, emotions: Im.EMOTION_MAX })
        return
      }
      const content = text.value,
        images = uploads.images.value.map((item) => ({
          id: item.id,
          width: item.width,
          height: item.height,
        }))
      busy.value = true
      text.value = ''
      uploads.hydrate([], null)
      emotions.value = false
      resize()
      const promise = im.send(content, images)
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
      window.removeEventListener('focus', focus)
      document.removeEventListener('visibilitychange', focus)
    })
    return {
      navMengIcon,
      messagePhotoIcon,
      messageSendIcon,
      messageRefreshIcon,
      messageCloseIcon,
      t,
      im,
      Im,
      MessageKind,
      text,
      error,
      field,
      input,
      scroll,
      emotions,
      busy,
      count,
      remaining,
      disabled,
      valid,
      rows,
      formatTime,
      resize,
      earlier,
      scrollHistory,
      insert,
      send,
      keydown,
      picked,
      paste,
      uploads,
      Emotions,
    }
  },
})
