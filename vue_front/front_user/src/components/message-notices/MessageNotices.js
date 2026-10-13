import { defineComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { usePostTime } from '@/composables/usePostTime'
import { RouteName } from '@/router/names'
import { MessageTab, NoticeType } from '@/constants/im'
import CommentBody from '@/components/comment-body/CommentBody.vue'
import FeedPagination from '@/components/feed-pagination/FeedPagination.vue'
import MessageAvatar from '@/components/message-avatar/MessageAvatar.vue'
export default defineComponent({
  name: 'MessageNotices',
  components: { MessageAvatar, CommentBody, FeedPagination },
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
  setup() {
    const { t, tm, rt } = useI18n(),
      router = useRouter()
    function open(item) {
      const notice = item.notice
      if (notice.postId)
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
      relativeTime: usePostTime(),
      MessageTab,
      NoticeType,
    }
  },
})
