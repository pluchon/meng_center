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
  growthFlagIcon,
  messageHeartIcon,
  messageShieldIcon,
  navGrowthIcon,
  navMessageIcon,
} from '@/assets/images'
import CommentBody from '@/components/comment-body/CommentBody.vue'
import FeedPagination from '@/components/feed-pagination/FeedPagination.vue'
import MessageAvatar from '@/components/message-avatar/MessageAvatar.vue'
import PageState from '@/components/page-state/PageState.vue'
// 每种通知对应的小图标
const typeIcons = {
  [NoticeType.COMPANION_STAGE_REACHED]: authHeartIcon,
  [NoticeType.POST_COMMENT]: authCodeIcon,
  [NoticeType.COMMENT_REPLY]: authCodeIcon,
  [NoticeType.COMMENT_MENTION]: authCodeIcon,
  [NoticeType.POST_LIKED]: messageHeartIcon,
  [NoticeType.COMMENT_LIKED]: messageHeartIcon,
  [NoticeType.POST_FAVORITED]: growthFlagIcon,
  [NoticeType.LEVEL_CHANGED]: navGrowthIcon,
  [NoticeType.USER_APPROVED]: authHeartIcon,
  [NoticeType.FOLLOWED]: authHeartIcon,
  [NoticeType.POST_REVIEW_CHANGED]: messageShieldIcon,
  [NoticeType.COMMENT_REVIEW_CHANGED]: messageShieldIcon,
  [NoticeType.PROFILE_REVIEW_CHANGED]: messageShieldIcon,
}
export default defineComponent({
  name: 'MessageNotices',
  components: { MessageAvatar, CommentBody, FeedPagination, PageState },
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
      if (notice.type === NoticeType.COMPANION_STAGE_REACHED)
        void router.push({name:RouteName.MENG})
      else if (notice.type === NoticeType.FOLLOWED && item.actors.length)
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
      typeIcon: (item) => typeIcons[item.notice.type] || navMessageIcon,
      relativeTime: usePostTime(),
      MessageTab,
      NoticeType,
    }
  },
})
