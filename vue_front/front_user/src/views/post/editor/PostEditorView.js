import { defineComponent, computed, ref, reactive, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { confirmDialog } from '@/composables/useConfirm'
import {
  ArrowLeft,
  Plus,
  Close,
  Refresh,
  Picture as PictureIcon,
  VideoCamera,
  MagicStick,
  Upload,
  EditPen,
  Link,
  List,
  ChatLineSquare,
  MoreFilled,
} from '@element-plus/icons-vue'
import { RouteName } from '@/router/names'
import { PostCategory, Categories, PostEditor, MarkdownFormats } from '@/constants/content'
import { contentApi } from '@/api/content'
import { useEditorUploads } from '@/composables/useEditorUploads'
import { notifyFailure } from '@/composables/useFailureNotice'
import MarkdownEditor from '@/components/markdown-editor/MarkdownEditor.vue'
import MarkdownBody from '@/components/markdown-body/MarkdownBody.vue'
import { articleEditAvatar } from '@/assets/images'
import LiquidEther from '@/components/liquid-ether/LiquidEther.vue'
export default defineComponent({
  name: 'PostEditorView',
  components: {
    LiquidEther,
    MarkdownEditor,
    MarkdownBody,
    ArrowLeft,
    Plus,
    Close,
    Refresh,
    PictureIcon,
    VideoCamera,
    MagicStick,
    Upload,
    EditPen,
  },
  setup() {
    const { t } = useI18n(),
      route = useRoute(),
      router = useRouter(),
      uploads = useEditorUploads(),
      editor = ref(null),
      imageInput = ref(null),
      coverInput = ref(null),
      preview = ref(false),
      video = ref(false),
      loading = ref(false),
      loadError = ref(false),
      busy = ref(false),
      draftId = ref(null),
      tagInput = ref(''),
      tagField = ref(null),
      tagFocused = ref(false),
      highlightedTag = ref(-1),
      suggestions = ref([]),
      errors = reactive({ title: '', body: '', images: '', tags: '' })
    const form = reactive({ category: PostCategory.DAILY, title: '', content: '', tags: [] }),
      count = (value) => Array.from(value).length
    const editing = computed(() => route.name === RouteName.POST_EDIT)
    const titleCount = computed(() => count(form.title)),
      bodyCount = computed(() => count(form.content))
    const signature = () =>
      JSON.stringify({
        form,
        images: uploads.images.value.map((image) => image.id || image.key),
        cover: uploads.cover.value?.id || uploads.cover.value?.key || null,
      })
    let baseline = signature(),
      allowLeave = false,
      tagTimer,
      tagGeneration = 0,
      loadGeneration = 0
    // A reactive baseline makes dirty state update after saving without changing the form.
    const saved = ref(baseline)
    const hasChanges = computed(() => signature() !== saved.value)
    function markSaved() {
      baseline = signature()
      saved.value = baseline
    }
    const unavailable = () => ElMessage.info(t('home.unavailable'))
    const formatIcons = {
      quote: ChatLineSquare,
      list: List,
      code: MoreFilled,
      link: Link,
    }
    const formats = MarkdownFormats.map((item) => ({ ...item, icon: formatIcons[item.key] }))
    function payload() {
      return {
        ...form,
        title: form.title.trim(),
        tags: [...form.tags],
        imageIds: uploads.images.value
          .filter((image) => image.status === 'ready')
          .map((image) => image.id),
        coverImageId: uploads.cover.value?.status === 'ready' ? uploads.cover.value.id : null,
      }
    }
    function tagKey(value) {
      return value
        .replace(/[！-～]/g, (char) => String.fromCharCode(char.charCodeAt(0) - 0xfee0))
        .replace(/\u3000/g, ' ')
        .trim()
        .toLowerCase()
    }
    const tagOptions = computed(() => {
      const key = tagKey(tagInput.value)
      const selected = new Set(form.tags.map(tagKey))
      return suggestions.value
        .filter((name) => key && tagKey(name).startsWith(key) && !selected.has(tagKey(name)))
        .sort((a, b) => Number(tagKey(b) === key) - Number(tagKey(a) === key))
    })
    const tagMenuVisible = computed(() =>
      Boolean(
        tagFocused.value &&
        tagOptions.value.length &&
        form.tags.length < PostEditor.TAG_MAX &&
        !busy.value &&
        !loading.value &&
        !loadError.value,
      ),
    )
    function navigateTags(direction) {
      tagFocused.value = true
      if (!tagOptions.value.length) return
      highlightedTag.value =
        (highlightedTag.value + direction + tagOptions.value.length) % tagOptions.value.length
    }
    function confirmTag(event) {
      if (event.isComposing || !tagInput.value.trim()) return
      addTag(
        tagMenuVisible.value && highlightedTag.value >= 0
          ? tagOptions.value[highlightedTag.value]
          : tagInput.value,
      )
    }
    function addTag(value = tagInput.value) {
      const key = tagKey(value)
      errors.tags = ''
      if (
        !key ||
        count(key) > PostEditor.TAG_LENGTH ||
        /[\s#]/u.test(key) ||
        form.tags.length >= PostEditor.TAG_MAX
      ) {
        errors.tags = t('editor.error.tags', {
          max: PostEditor.TAG_MAX,
          length: PostEditor.TAG_LENGTH,
        })
        return
      }
      if (form.tags.some((tag) => tagKey(tag) === key)) {
        errors.tags = t('editor.error.duplicateTag')
        return
      }
      form.tags.push(value.trim())
      tagInput.value = ''
      suggestions.value = []
      highlightedTag.value = -1
    }
    watch(tagInput, (value) => {
      clearTimeout(tagTimer)
      const generation = ++tagGeneration
      suggestions.value = []
      highlightedTag.value = -1
      if (!value.trim() || form.tags.length >= PostEditor.TAG_MAX) return
      tagTimer = setTimeout(async () => {
        try {
          const result = await contentApi.tags({ keyword: value.trim() })
          if (generation === tagGeneration) {
            suggestions.value = result
            highlightedTag.value = tagOptions.value.length ? 0 : -1
          }
        } catch (error) {
          notifyFailure(error)
        }
      }, PostEditor.TAG_DEBOUNCE)
    })
    async function loadDraft() {
      const id = editing.value ? route.params.id : route.query.draft
      if (!id) {
        ++loadGeneration
        draftId.value = null
        loading.value = false
        loadError.value = false
        Object.assign(form, { category: PostCategory.DAILY, title: '', content: '', tags: [] })
        Object.keys(errors).forEach((key) => {
          errors[key] = ''
        })
        uploads.hydrate([], null)
        markSaved()
        return
      }
      if (!editing.value && String(draftId.value) === String(id)) return
      loading.value = true
      loadError.value = false
      const generation = ++loadGeneration
      try {
        const data = await (editing.value ? contentApi.detail(id) : contentApi.draft(id))
        if (generation !== loadGeneration) return
        if (editing.value && String(data.author.id) !== String(useUserStore().current?.id)) {
          loadError.value = true
          return
        }
        draftId.value = editing.value ? null : data.id
        Object.assign(form, {
          category: data.category || PostCategory.DAILY,
          title: data.title || '',
          content: data.content || '',
          tags: data.tags || [],
        })
        uploads.hydrate(
          data.images,
          data.cover && !data.images.some((image) => image.id === data.cover.id)
            ? data.cover
            : null,
        )
        markSaved()
      } catch (error) {
        if (generation === loadGeneration) {
          loadError.value = true
          notifyFailure(error)
        }
      } finally {
        if (generation === loadGeneration) loading.value = false
      }
    }
    async function confirmLeave() {
      if (busy.value && !allowLeave) return false
      if (allowLeave || !hasChanges.value) return true
      return confirmDialog({
        message: t('editor.leaveMessage'),
        title: t('editor.leaveTitle'),
        confirmText: t('editor.leave'),
        cancelText: t('editor.stay'),
      })
    }
    onBeforeRouteLeave(confirmLeave)
    onBeforeRouteUpdate(async (to, from) =>
      to.query.draft === from.query.draft || String(to.query.draft) === String(draftId.value)
        ? true
        : confirmLeave(),
    )
    watch(() => [route.query.draft, route.params.id, route.name], loadDraft)
    watch(
      () => form.content,
      () => {
        errors.body = ''
      },
    )
    function unload(event) {
      if (hasChanges.value && !allowLeave) {
        event.preventDefault()
        event.returnValue = ''
      }
    }
    onMounted(() => {
      window.addEventListener('beforeunload', unload)
      void loadDraft()
    })
    onUnmounted(() => {
      window.removeEventListener('beforeunload', unload)
      clearTimeout(tagTimer)
      tagGeneration++
      loadGeneration++
    })
    function validate(publishing) {
      errors.title = ''
      errors.body = ''
      errors.images = ''
      if (publishing && (!form.title.trim() || titleCount.value > PostEditor.TITLE_MAX))
        errors.title = t('editor.error.title', { max: PostEditor.TITLE_MAX })
      if (publishing && (!form.content.trim() || bodyCount.value > PostEditor.BODY_MAX))
        errors.body = t('editor.error.body', { max: PostEditor.BODY_MAX })
      if (!publishing) {
        if (!form.title.trim() && !form.content.trim()) errors.body = t('editor.error.draft')
        if (titleCount.value > PostEditor.TITLE_MAX)
          errors.title = t('editor.error.title', { max: PostEditor.TITLE_MAX })
        if (bodyCount.value > PostEditor.BODY_MAX)
          errors.body = t('editor.error.body', { max: PostEditor.BODY_MAX })
      }
      if (uploads.blocked.value) errors.images = t('editor.error.upload')
      return !errors.title && !errors.body && !errors.images
    }
    async function submit(publishing) {
      if (busy.value || loading.value || loadError.value || !validate(publishing)) return
      busy.value = true
      try {
        if (publishing) {
          const id = editing.value
            ? route.params.id
            : await contentApi.publish({ ...payload(), draftId: draftId.value })
          if (editing.value) await contentApi.update(id, payload())
          allowLeave = true
          markSaved()
          ElMessage.success(t(editing.value ? 'editor.updated' : 'editor.published'))
          await router.push({
            name: RouteName.POST_DETAIL,
            params: { id },
            query: editing.value ? route.query : {},
          })
        } else {
          draftId.value = await contentApi.saveDraft(draftId.value, payload())
          markSaved()
          await router.replace({
            name: RouteName.POST_NEW,
            query: { draft: String(draftId.value) },
          })
          ElMessage.success(t('editor.saved'))
        }
      } catch (error) {
        if (error.status === 400 || error.status === 409) errors.body = error.message
        else notifyFailure(error)
      } finally {
        busy.value = false
      }
    }
    function paste(event) {
      if (busy.value || loading.value) return
      const files = Array.from(event.clipboardData?.files || [])
      if (files.length) {
        event.preventDefault()
        uploads.add(files)
      }
    }
    function drop(event, cover = false) {
      event.preventDefault()
      if (!busy.value) uploads.add(event.dataTransfer.files, cover)
    }
    function picked(event, asCover = false) {
      uploads.add(event.target.files, asCover)
      event.target.value = ''
    }
    const slots = computed(() =>
      Array.from({ length: PostEditor.IMAGE_MAX }, (_, index) => ({
        index,
        item: uploads.images.value[index],
        add: index === uploads.images.value.length,
      })),
    )
    const coverPreview = computed(
      () => uploads.cover.value || uploads.images.value.find((image) => image.status === 'ready'),
    )
    return {
      t,
      editing,
      articleEditAvatar,
      RouteName,
      PostEditor,
      Categories,
      form,
      editor,
      imageInput,
      coverInput,
      preview,
      video,
      loading,
      loadError,
      busy,
      tagInput,
      tagField,
      tagFocused,
      highlightedTag,
      tagOptions,
      tagMenuVisible,
      navigateTags,
      confirmTag,
      errors,
      titleCount,
      bodyCount,
      formats,
      addTag,
      unavailable,
      submit,
      loadDraft,
      paste,
      drop,
      picked,
      slots,
      coverPreview,
      images: uploads.images,
      cover: uploads.cover,
      uploadNotice: uploads.notice,
      removeImage: uploads.remove,
      retryImage: uploads.retry,
    }
  },
})
