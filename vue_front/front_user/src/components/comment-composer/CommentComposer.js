import { computed, defineComponent, nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Close, Plus, Picture as PictureIcon, Refresh } from '@element-plus/icons-vue'
import { IconMoodSmile } from '@tabler/icons-vue'
import CollapsePanel from '@/components/collapse-panel/CollapsePanel.vue'
import { contentApi } from '@/api/content'
import { userAvatar } from '@/assets/images'
import { useUserStore } from '@/stores/user'
import { useEditorUploads } from '@/composables/useEditorUploads'
import { Comment, Emotions, emotionToken } from '@/constants/comments'
import { notifyFailure } from '@/composables/useFailureNotice'
export default defineComponent({
  name: 'CommentComposer',
  components: { Close, Plus, PictureIcon, IconMoodSmile, Refresh, CollapsePanel },
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
      error = ref('')
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
        })
        const target = props.target
        text.value = ''
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
      error: computed(() => {
        if (attachmentCount.value > Comment.ATTACHMENTS)
          return t('detail.error.attachments', { max: Comment.ATTACHMENTS })
        return error.value
      }),
      count,
      attachmentCount,
      selectedEmotions,
      removeEmotion,
      valid,
      Comment,
      Emotions,
      insert,
      send,
      resize,
      limit,
      picked,
      paste,
      images: uploads.images,
      notice: uploads.notice,
      blocked: uploads.blocked,
      remove: uploads.remove,
      retry: uploads.retry,
    }
  },
})
