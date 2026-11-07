import { computed, defineComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { usePostTime } from '@/composables/usePostTime'
import { RouteName } from '@/router/names'
import { MessageTab, NoticeType } from '@/constants/im'
import { CreatorTab } from '@/constants/content'
import {
  authCodeIcon,
  authHeartIcon,
  messageHeartIcon,
  messageShieldIcon,
  navGrowthIcon,
  navMessageIcon,
} from '@/assets/images'
import { IconNotes } from '@tabler/icons-vue'
import { commentSegments } from '@/constants/comments'
import FeedPagination from '@/components/feed-pagination/FeedPagination.vue'
import MessageAvatar from '@/components/message-avatar/MessageAvatar.vue'
import PageState from '@/components/page-state/PageState.vue'
// 这几种通知的帖子标题嵌在第一行里：「谁 在《标题》评论了你的帖子」；其余的标题放第二行
const INLINE_POST = new Set([
  NoticeType.POST_COMMENT,
  NoticeType.COMMENT_REPLY,
  NoticeType.COMMENT_MENTION,
])
// 评论摘要那一行最多带几个表情，多的显示成「+N」
const EXCERPT_EMOTIONS = 6
export default defineComponent({
  name: 'MessageNotices',
  components: { IconNotes, MessageAvatar, FeedPagination, PageState },
  props: {
    tab: { type: String, required: true },
    group: { type: String, required: true },
    records: { type: Array, required: true },
    total: { type: Number, required: true },
    page: { type: Number, required: true },
    totalPages: { type: Number, required: true },
    loading: Boolean,
    error: { type: String, default: '' },
  },
  emits: ['page', 'retry'],
  setup(props) {
    const { t, tm, rt } = useI18n(),
      router = useRouter()
    // 列表区当前显示哪一块：加载、出错、空、或某一页；换块时整块淡出淡入
    const view = computed(() =>
      props.loading && !props.records.length
        ? 'loading'
        : props.error
          ? 'error'
          : !props.records.length
            ? 'empty'
            : 'page-' + props.page,
    )
    const headerIcon = computed(() =>
      props.tab === MessageTab.COMMENT
        ? authCodeIcon
        : props.tab === MessageTab.LIKE
          ? messageHeartIcon
          : props.group === 'FOLLOW'
            ? authHeartIcon
            : props.group === 'GROWTH'
              ? navGrowthIcon
              : props.group === 'POST'
                ? navMessageIcon
                : messageShieldIcon,
    )
    function open(item) {
      const notice = item.notice
      if (notice.type === NoticeType.FOLLOWED && item.actors.length)
        void router.push({ name: RouteName.USER, params: { id: item.actors[0].id } })
      else if (notice.type === NoticeType.POST_REVIEW_CHANGED && !notice.postId)
        // 帖子被审核退回，原帖已不在，去草稿箱看
        void router.push({ name: RouteName.CREATOR, query: { tab: CreatorTab.DRAFTS } })
      else if (notice.postId)
        void router.push({
          name: RouteName.POST_DETAIL,
          params: { id: notice.postId },
          hash: notice.commentId ? '#comment-' + notice.commentId : '',
        })
      else
        void router.push({
          name: notice.type === NoticeType.LEVEL_CHANGED ? RouteName.GROWTH : RouteName.ACCOUNT,
        })
    }
    const names = (item) =>
      item.actors.map((actor) => actor.nickname || t('home.authorFallback')).join('、')
    // 评论摘要拆成一行文字和跟在后面的表情
    const excerptCache = new WeakMap()
    function excerpt(item) {
      const cached = excerptCache.get(item)
      if (cached) return cached
      const segments = commentSegments(item.notice.excerpt || '')
      const value = {
        text: segments
          .filter((segment) => !segment.image)
          .map((segment) => segment.text)
          .join('')
          .replace(/\s+/g, ' ')
          .trim(),
        emotions: segments.filter((segment) => segment.image),
      }
      excerptCache.set(item, value)
      return value
    }
    // 原评论只占一行小字，表情记号换成「[表情]」
    const plain = (value) =>
      value.replace(/\[表情:[^\s[\]]{1,12}\]/gu, t('im.emotionPreview')).replace(/\s+/g, ' ')
    function levelName(level) {
      const names = tm('growth.levels')
      return names[level - 1] ? rt(names[level - 1]) : ''
    }
    return {
      t,
      open,
      names,
      levelName,
      view,
      headerIcon,
      excerpt,
      plain,
      inlinePost: (item) => INLINE_POST.has(item.notice.type) && Boolean(item.notice.postTitle),
      EXCERPT_EMOTIONS,
      relativeTime: usePostTime(),
      MessageTab,
      NoticeType,
    }
  },
})
