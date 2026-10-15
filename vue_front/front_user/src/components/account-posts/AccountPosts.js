import {
  computed,
  defineComponent,
  nextTick,
  onMounted,
  onUnmounted,
  reactive,
  ref,
  watch,
} from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  IconArrowBackUp as Back,
  IconTrash as Delete,
  IconPencil as EditPen,
  IconStar as Star,
  IconHeart,
  IconCheck as Check,
  IconLock,
  IconWorld,
} from '@tabler/icons-vue'
import { contentApi, contentImage } from '@/api/content'
import { useRequestResource } from '@/composables/useRequestResource'
import { notifyFailure } from '@/composables/useFailureNotice'
import { Account, AccountPane, FolderVisibility } from '@/constants/account'
import { accountPaletteIcon, accountScenes } from '@/assets/images'
import { accountDate } from '@/utils/accountDate'
import { RouteName } from '@/router/names'
import PageState from '@/components/page-state/PageState.vue'
import FeedPagination from '@/components/feed-pagination/FeedPagination.vue'
import FolderColorPicker from '@/components/folder-color-picker/FolderColorPicker.vue'
export default defineComponent({
  name: 'AccountPosts',
  components: {
    Back,
    Delete,
    EditPen,
    Check,
    Star,
    IconHeart,
    IconLock,
    IconWorld,
    PageState,
    FeedPagination,
    FolderColorPicker,
  },
  props: {
    // 别人的主页传他的编号：只看他公开的收藏夹和发布的帖子，没有任何修改入口
    userId: { type: String, default: null },
    pane: { type: String, default: AccountPane.LIKES },
    folderId: { type: String, default: null },
    folder: { type: Object, default: null },
    page: { type: Number, default: 1 },
    deleting: Boolean,
  },
  emits: ['page', 'back', 'folder-updated', 'delete', 'changed'],
  setup(props, { emit }) {
    const { t } = useI18n()
    const own = computed(() => !props.userId)
    const editing = reactive({ name: false, description: false })
    const draft = reactive({ name: '', description: '' })
    const saving = ref(null)
    const nameInput = ref(null),
      descriptionInput = ref(null)
    const nameEdit = ref(null),
      descriptionEdit = ref(null),
      colorBox = ref(null),
      colorOpen = ref(false)
    // 夹内的主题色跟着收藏夹的颜色走，喜欢页没有收藏夹，用默认的金色
    const tone = computed(() => (props.folder?.color || 'GOLD').toLowerCase())
    const inputFor = (field) => (field === 'name' ? nameInput.value : descriptionInput.value)
    const buttonFor = (field) => (field === 'name' ? nameEdit.value : descriptionEdit.value)
    async function editField(field) {
      if (!props.folder || props.folder.isDefault || saving.value) return
      if (!editing[field]) draft[field] = props.folder[field] || ''
      editing[field] = true
      await nextTick()
      inputFor(field)?.focus()
      inputFor(field)?.select()
    }
    async function finishField(field) {
      editing[field] = false
      await nextTick()
      buttonFor(field)?.focus()
    }
    async function saveField(field) {
      if (!props.folder || props.folder.isDefault || !editing[field] || saving.value) return
      const value = draft[field].trim()
      if (
        field === 'name'
          ? !value || Array.from(value).length > Account.NAME_MAX
          : Array.from(value).length > Account.BIO_MAX
      ) {
        ElMessage.warning(
          t(field === 'name' ? 'account.invalidName' : 'account.invalidFolderDescription'),
        )
        inputFor(field)?.focus()
        return
      }
      if (value === (props.folder[field] || '')) {
        await finishField(field)
        return
      }
      saving.value = field
      try {
        const change = {
          name: props.folder.name,
          description: props.folder.description || '',
          [field]: value,
        }
        await contentApi.updateFolder(props.folder.id, change)
        emit('folder-updated', { id: props.folder.id, ...change })
        editing[field] = false
        ElMessage.success(t('account.saved'))
      } catch (error) {
        notifyFailure(error)
      } finally {
        saving.value = null
        await nextTick()
        if (editing[field]) inputFor(field)?.focus()
        else buttonFor(field)?.focus()
      }
    }
    // 公开或私密：别人能不能在他的主页看到这个收藏夹
    async function toggleVisibility() {
      if (!props.folder || saving.value) return
      const visibility =
        props.folder.visibility === FolderVisibility.PUBLIC
          ? FolderVisibility.PRIVATE
          : FolderVisibility.PUBLIC
      saving.value = 'visibility'
      try {
        await contentApi.changeFolderVisibility(props.folder.id, visibility)
        emit('folder-updated', { id: props.folder.id, visibility })
        ElMessage.success(
          t(
            visibility === FolderVisibility.PUBLIC
              ? 'account.visibilityPublicDone'
              : 'account.visibilityPrivateDone',
          ),
        )
      } catch (error) {
        notifyFailure(error)
      } finally {
        saving.value = null
      }
    }
    async function saveColor(color) {
      if (!props.folder || props.folder.isDefault || saving.value) return
      colorOpen.value = false
      if (color === props.folder.color) return
      saving.value = 'color'
      try {
        const change = {
          name: props.folder.name,
          description: props.folder.description || '',
          color,
        }
        await contentApi.updateFolder(props.folder.id, change)
        emit('folder-updated', { id: props.folder.id, ...change })
        ElMessage.success(t('account.saved'))
      } catch (error) {
        notifyFailure(error)
      } finally {
        saving.value = null
      }
    }
    // 点菜单以外的地方、按 Esc 都收起颜色菜单
    function closeColor(event) {
      if (!colorOpen.value) return
      if (event.type === 'keydown') {
        if (event.key === 'Escape') colorOpen.value = false
      } else if (!colorBox.value?.contains(event.target)) colorOpen.value = false
    }
    onMounted(() => {
      document.addEventListener('pointerdown', closeColor)
      document.addEventListener('keydown', closeColor)
    })
    onUnmounted(() => {
      document.removeEventListener('pointerdown', closeColor)
      document.removeEventListener('keydown', closeColor)
    })
    function editKey(field, event) {
      if (event.isComposing || saving.value) return
      if (event.key === 'Enter') {
        event.preventDefault()
        void saveField(field)
      } else if (event.key === 'Escape') {
        event.preventDefault()
        void finishField(field)
      }
    }
    const viewport = [
      '(min-width: 900px)',
      '(max-height: 650px)',
      '(max-height: 800px)',
      '(max-height: 960px)',
    ].map((query) => window.matchMedia(query))
    const visibleSize = () =>
      !viewport[0].matches
        ? Account.SIZE
        : viewport[1].matches
          ? 2
          : viewport[2].matches
            ? 4
            : viewport[3].matches
              ? 6
              : Account.SIZE
    const pageSize = ref(visibleSize())
    const resize = () => {
      const size = visibleSize()
      if (pageSize.value === size) return
      pageSize.value = size
    }
    const { state: resource, load } = useRequestResource(
      async (signal) => {
        const params = { page: props.page, size: pageSize.value }
        const config = { signal }
        let result
        if (props.userId && props.folderId)
          result = await contentApi.userFolderPosts(props.userId, props.folderId, params, config)
        else if (props.userId) {
          result = await contentApi.userPosts(props.userId, params, config)
          result.records = result.records.map((post) => ({
            ...post,
            cover: post.images[0]?.thumbUrl || post.images[0]?.url || null,
            savedAt: post.createTime,
          }))
        } else if (props.folderId) result = await contentApi.folderPosts(props.folderId, params, config)
        else result = await contentApi.likedPosts(params, config)
        return {
          ...result,
          records: result.records.map((post) => ({ ...post, active: true, pending: false })),
        }
      },
      { records: [], total: 0 },
    )
    const total = computed(
      () => resource.data.total - resource.data.records.filter((post) => !post.active).length,
    )
    async function toggle(post) {
      if (post.pending) return
      post.pending = true
      const on = !post.active
      try {
        if (props.folderId) {
          await (on ? contentApi.addToFolder : contentApi.removeFromFolder)(props.folderId, post.id)
        } else await contentApi.like(post.id, on)
        post.active = on
        emit('changed')
      } catch (error) {
        notifyFailure(error)
      } finally {
        post.pending = false
      }
    }
    onMounted(load)
    onMounted(() => viewport.forEach((query) => query.addEventListener('change', resize)))
    onUnmounted(() => viewport.forEach((query) => query.removeEventListener('change', resize)))
    watch([() => props.page, pageSize], ([page, size], [, previousSize]) => {
      if (size !== previousSize && page !== 1) {
        emit('page', 1)
        return
      }
      void load()
    })
    // 卡片右下角的时间：收藏于、喜欢于、发布于
    const timeKey = computed(() =>
      props.folderId ? 'account.savedAt' : props.userId ? 'account.publishedAt' : 'account.likedAt',
    )
    return {
      t,
      Account,
      FolderVisibility,
      own,
      timeKey,
      toggleVisibility,
      editing,
      draft,
      saving,
      nameInput,
      descriptionInput,
      nameEdit,
      descriptionEdit,
      editField,
      saveField,
      editKey,
      tone,
      colorBox,
      colorOpen,
      saveColor,
      paletteIcon: accountPaletteIcon,
      resource,
      load,
      total,
      toggle,
      contentImage,
      accountScenes,
      RouteName,
      pageSize,
      date: (value) => accountDate(value, t, true),
    }
  },
})
