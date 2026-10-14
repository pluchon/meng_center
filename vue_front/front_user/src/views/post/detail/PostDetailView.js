import {
  computed,
  defineComponent,
  onMounted,
  onUnmounted,
  reactive,
  ref,
  shallowRef,
  watch,
} from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  Location,
  ChatDotRound,
  View as ViewIcon,
  EditPen,
  Delete,
} from '@element-plus/icons-vue'
import { IconHeart, IconStar, IconFlag } from '@tabler/icons-vue'
import { RouteName } from '@/router/names'
import { Comment, ReportTarget } from '@/constants/comments'
import { contentApi } from '@/api/content'
import {
  communitySparkle,
  detailArtwork,
  detailArtworkSmall,
  detailMobileArtwork,
  detailBubble,
  detailFlower,
} from '@/assets/images'
import { useUserStore } from '@/stores/user'
import { useFavoriteStore } from '@/stores/favorites'
import { useCommunity } from '@/composables/useCommunity'
import { usePostTime } from '@/composables/usePostTime'
import { notifyFailure } from '@/composables/useFailureNotice'
import CommunityLayout from '@/layouts/community/CommunityLayout.vue'
import MarkdownBody from '@/components/markdown-body/MarkdownBody.vue'
import PageState from '@/components/page-state/PageState.vue'
import PostImages from '@/components/post-images/PostImages.vue'
import PostComments from '@/components/post-comments/PostComments.vue'
import ReportDialog from '@/components/report-dialog/ReportDialog.vue'
const detailScrollbarClass = 'detail-scrollbar-hidden'
import UserContact from '@/components/user-contact/UserContact.vue'
export default defineComponent({
  name: 'PostDetailView',
  components: {
    UserContact,
    CommunityLayout,
    MarkdownBody,
    PageState,
    PostImages,
    PostComments,
    ReportDialog,
    ArrowLeft,
    Location,
    IconHeart,
    IconStar,
    IconFlag,
    ChatDotRound,
    ViewIcon,
    EditPen,
    Delete,
  },
  setup() {
    onMounted(() => document.documentElement.classList.add(detailScrollbarClass))
    onUnmounted(() => document.documentElement.classList.remove(detailScrollbarClass))
    const route = useRoute(),
      router = useRouter(),
      user = useUserStore(),
      { t } = useCommunity(),
      post = ref(null),
      initialComments = shallowRef(null),
      loading = ref(false),
      error = ref(null),
      reportTarget = ref(null),
      pending = reactive({ like: false, favorite: false, delete: false })
    let generation = 0
    const own = computed(
      () => post.value && String(post.value.author.id) === String(user.current?.id),
    )
    const homeLink = computed(() => ({
      name:
        route.query.from === 'account'
          ? RouteName.ACCOUNT
          : route.query.from === 'creator'
            ? RouteName.CREATOR
            : route.query.from === 'search'
              ? RouteName.SEARCH
              : RouteName.HOME,
      query: Object.fromEntries(
        ['q', 'category', 'sort', 'page', 'tab', 'pane', 'folder']
          .filter((key) => route.query[key])
          .map((key) => [key, route.query[key]]),
      ),
    }))
    async function load() {
      const current = ++generation
      loading.value = true
      error.value = null
      post.value = null
      reportTarget.value = null
      initialComments.value = contentApi
        .comments(route.params.id, { page: 1, size: Comment.SIZE })
        .then(
          (result) => ({ result }),
          (error) => ({ error }),
        )
      try {
        const result = await contentApi.detail(route.params.id)
        if (current === generation) post.value = result
      } catch (e) {
        if (current === generation) error.value = e
      } finally {
        if (current === generation) loading.value = false
      }
    }
    watch(() => route.params.id, load, { immediate: true })
    onUnmounted(() => generation++)
    async function toggle(kind) {
      if (pending[kind] || !post.value) return
      const item = post.value,
        field = kind === 'like' ? 'liked' : 'favorited',
        count = kind === 'like' ? 'likeCount' : 'favoriteCount',
        old = item[field]
      pending[kind] = true
      if (kind === 'favorite') {
        try {
          await useFavoriteStore().manage(item, ({ favorited, favoriteCount }) => {
            item.favorited = favorited
            item.favoriteCount = favoriteCount
          })
        } catch (error) {
          notifyFailure(error)
        } finally {
          pending[kind] = false
        }
        return
      }
      item[field] = !old
      item[count] += old ? -1 : 1
      try {
        await contentApi[kind](item.id, !old)
      } catch (e) {
        item[field] = old
        item[count] += old ? 1 : -1
        notifyFailure(e)
      } finally {
        pending[kind] = false
      }
    }
    async function remove() {
      if (pending.delete) return
      try {
        await ElMessageBox.confirm(t('detail.deletePost'), t('detail.delete'), {
          confirmButtonText: t('detail.confirm'),
          cancelButtonText: t('detail.cancel'),
          type: 'warning',
        })
      } catch {
        return
      }
      pending.delete = true
      try {
        await contentApi.delete(post.value.id)
        ElMessage.success(t('detail.removed'))
        await router.push(homeLink.value)
      } catch (e) {
        notifyFailure(e)
      } finally {
        pending.delete = false
      }
    }
    function scrollComments() {
      document.getElementById('post-comments')?.scrollIntoView({
        behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
      })
    }
    return {
      t,
      RouteName,
      ReportTarget,
      post,
      initialComments,
      loading,
      error,
      load,
      own,
      pending,
      toggle,
      remove,
      homeLink,
      reportTarget,
      scrollComments,
      communitySparkle,
      detailArtwork,
      detailArtworkSmall,
      detailMobileArtwork,
      detailBubble,
      detailFlower,
      relativeTime: usePostTime(),
    }
  },
})
