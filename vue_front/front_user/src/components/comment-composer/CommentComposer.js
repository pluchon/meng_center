import { computed, defineComponent, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Close, Plus, Picture as PictureIcon, Refresh } from '@element-plus/icons-vue'
import { IconMoodSmile, IconSend } from '@tabler/icons-vue'
import CollapsePanel from '@/components/collapse-panel/CollapsePanel.vue'
import EmotionPanel from '@/components/emotion-panel/EmotionPanel.vue'
import { contentApi } from '@/api/content'
import { userAvatar, communitySparkle } from '@/assets/images'
import { avatarUrl } from '@/api/user'
import { useUserStore } from '@/stores/user'
import { useEditorUploads } from '@/composables/useEditorUploads'
import { Comment, emotionToken } from '@/constants/comments'
import { notifyFailure } from '@/composables/useFailureNotice'
export default defineComponent({
  name: 'CommentComposer',
  components: {
    Close,
    Plus,
    PictureIcon,
    IconMoodSmile,
    IconSend,
    Refresh,
    CollapsePanel,
    EmotionPanel,
  },
  props: {
    postId: { type: [String, Number], required: true },
    target: { type: Object, default: null },
  },
  emits: ['sent', 'clear-target'],
  setup(props, { emit, expose }) {
    const user = useUserStore()
    const { t } = useI18n(),
      uploads = useEditorUploads(contentApi.uploadComment),
      text = ref(''),
      selectedEmotions = ref([]),
      field = ref(null),
      input = ref(null),
      panel = ref(false),
      busy = ref(false),
      error = ref(''),
      candidates = ref([]),
      candidatesReady = ref(false),
      mentionOpen = ref(false),
      mentionWord = ref(''),
      mentionActive = ref(0),
      chosen = ref([])
    let mentionStart = -1,
      candidatesRequested = false
    // 超出张数、图片不合规这类提示统一用顶部的消息弹框，不在输入框上面插一行红字
    const tip = computed(() => {
      if (attachmentCount.value > Comment.ATTACHMENTS)
        return t('detail.error.attachments', { max: Comment.ATTACHMENTS })
      return error.value
    })
    const content = computed(
      () => text.value + selectedEmotions.value.map((item) => emotionToken(item.name)).join(''),
    )
    const count = computed(() => Array.from(content.value).length)
    const attachmentCount = computed(
      () =>
        uploads.images.value.length +
        (content.value.match(/\[表情:[^\s[\]]{1,12}\]/gu) || []).length,
    )
    const valid = computed(
      () =>
        Boolean(content.value.trim() || uploads.images.value.length) &&
        count.value <= Comment.LENGTH &&
        attachmentCount.value <= Comment.ATTACHMENTS &&
        !uploads.blocked.value,
    )
    watch(tip, (message) => {
      if (message) ElMessage.warning(message)
    })
    watch(
      () => uploads.notice.value,
      (message) => {
        if (message) ElMessage.warning(message)
      },
      { flush: 'sync' },
    )
    function focus() {
      void nextTick(() => field.value?.focus({ preventScroll: true }))
    }
    expose({ focus })
    // 总字数封顶：表情也占字数，超出的部分直接截掉，不再输入（输入法组词中先不截）
    function limit(event) {
      if (event?.isComposing) return
      const room = Comment.LENGTH - Array.from(content.value.slice(text.value.length)).length
      const chars = Array.from(text.value)
      if (chars.length > room) text.value = chars.slice(0, Math.max(0, room)).join('')
      resize()
    }
    const matches = computed(() => {
      const word = mentionWord.value.toLowerCase()
      return candidates.value.filter((item) => item.nickname.toLowerCase().includes(word)).slice(0, 8)
    })
    const showMentions = computed(
      () =>
        mentionOpen.value &&
        (matches.value.length > 0 || (candidatesReady.value && !candidates.value.length)),
    )
    async function loadCandidates() {
      if (candidatesRequested) return
      candidatesRequested = true
      try {
        candidates.value = await contentApi.mentionCandidates(props.postId)
        candidatesReady.value = true
      } catch (e) {
        candidatesRequested = false
        notifyFailure(e)
      }
    }
    function closeMention() {
      mentionOpen.value = false
      mentionStart = -1
    }
    // 光标前面紧跟着 @关键字 时打开候选
    function detect() {
      const el = field.value
      if (!el || busy.value) {
        closeMention()
        return
      }
      const caret = el.selectionStart
      const found = /(^|\s)@([^\s@]{0,20})$/u.exec(text.value.slice(0, caret))
      if (!found) {
        closeMention()
        return
      }
      const start = caret - found[2].length - 1
      if (!mentionOpen.value || start !== mentionStart || found[2] !== mentionWord.value)
        mentionActive.value = 0
      mentionStart = start
      mentionWord.value = found[2]
      mentionOpen.value = true
      void loadCandidates()
    }
    function pick(item) {
      const el = field.value
      const start = mentionStart
      const caret = el ? el.selectionStart : text.value.length
      const inserted = '@' + item.nickname + ' '
      text.value = text.value.slice(0, start) + inserted + text.value.slice(caret)
      if (!chosen.value.some((mention) => mention.userId === item.id))
        chosen.value.push({ userId: item.id, nickname: item.nickname })
      closeMention()
      void nextTick(() => {
        limit()
        field.value?.focus({ preventScroll: true })
        field.value?.setSelectionRange(start + inserted.length, start + inserted.length)
      })
    }
    function mentionKey(event) {
      if (!showMentions.value || !matches.value.length || event.isComposing) return
      const size = matches.value.length
      if (event.key === 'ArrowDown') {
        event.preventDefault()
        mentionActive.value = (mentionActive.value + 1) % size
      } else if (event.key === 'ArrowUp') {
        event.preventDefault()
        mentionActive.value = (mentionActive.value - 1 + size) % size
      } else if (event.key === 'Enter' || event.key === 'Tab') {
        event.preventDefault()
        pick(matches.value[mentionActive.value])
      } else if (event.key === 'Escape') {
        event.preventDefault()
        closeMention()
      }
    }
    function onInput(event) {
      limit(event)
      detect()
    }
    function resize() {
      if (field.value) {
        field.value.style.height = 'auto'
        field.value.style.height = field.value.scrollHeight + 'px'
      }
    }
    function insert(item) {
      // 字数已满时不再加入表情
      if (count.value + Array.from(emotionToken(item.name)).length > Comment.LENGTH) return
      if (attachmentCount.value >= Comment.ATTACHMENTS) {
        error.value = t('detail.error.attachments', { max: Comment.ATTACHMENTS })
        return
      }
      selectedEmotions.value.push({ ...item, key: crypto.randomUUID() })
      error.value = ''
      focus()
    }
    function removeEmotion(item) {
      selectedEmotions.value = selectedEmotions.value.filter((emotion) => emotion.key !== item.key)
      error.value = ''
    }
    function addFiles(files) {
      const available = Math.max(0, Comment.ATTACHMENTS - attachmentCount.value)
      const list = Array.from(files)
      uploads.add(list.slice(0, available))
      error.value =
        list.length > available ? t('detail.error.attachments', { max: Comment.ATTACHMENTS }) : ''
    }
    async function send() {
      if (busy.value || !valid.value) return
      busy.value = true
      error.value = ''
      try {
        const result = await contentApi.comment(props.postId, {
          content: content.value,
          imageIds: uploads.images.value.map((i) => i.id),
          replyToCommentId: props.target?.id ?? null,
          mentionUserIds: chosen.value
            .filter((mention) => text.value.includes('@' + mention.nickname))
            .map((mention) => mention.userId),
        })
        const target = props.target
        text.value = ''
        chosen.value = []
        closeMention()
        selectedEmotions.value = []
        uploads.hydrate([], null)
        panel.value = false
        resize()
        emit('sent', { comment: result, target })
      } catch (e) {
        if (e.status === 400) error.value = e.message
        else notifyFailure(e)
      } finally {
        busy.value = false
      }
    }
    function picked(event) {
      addFiles(event.target.files)
      event.target.value = ''
    }
    function paste(event) {
      if (busy.value) return
      const files = event.clipboardData?.files
      if (files?.length) {
        event.preventDefault()
        addFiles(files)
      }
    }
    return {
      avatar: computed(() => user.current?.avatar || userAvatar),
      t,
      text,
      field,
      input,
      panel,
      busy,
      count,
      attachmentCount,
      selectedEmotions,
      removeEmotion,
      valid,
      Comment,
      insert,
      matches,
      showMentions,
      mentionActive,
      pick,
      mentionKey,
      detect,
      closeMention,
      onInput,
      avatarUrl,
      userAvatar,
      communitySparkle,
      send,
      resize,
      limit,
      picked,
      paste,
      images: uploads.images,
      blocked: uploads.blocked,
      remove: uploads.remove,
      retry: uploads.retry,
    }
  },
})
