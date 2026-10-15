import { defineComponent, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Delete, ArrowDown, Location } from '@element-plus/icons-vue'
import { IconHeart, IconFlag, IconPin } from '@tabler/icons-vue'
import { useUserStore } from '@/stores/user'
import { usePostTime } from '@/composables/usePostTime'
import CommentBody from '@/components/comment-body/CommentBody.vue'
import PostImages from '@/components/post-images/PostImages.vue'
import UserContact from '@/components/user-contact/UserContact.vue'
export default defineComponent({
  name: 'CommentItem',
  components: {
    UserContact,
    CommentBody,
    PostImages,
    IconHeart,
    IconFlag,
    IconPin,
    Delete,
    ArrowDown,
    Location,
  },
  props: {
    comment: { type: Object, required: true },
    compact: Boolean,
    expanded: Boolean,
    busy: Boolean,
    // 当前用户是帖子作者，可以置顶一级评论
    canPin: Boolean,
  },
  emits: ['like', 'reply', 'delete', 'report', 'expand', 'pin'],
  setup(props) {
    const user = useUserStore()
    return {
      ...useI18n(),
      relativeTime: usePostTime(),
      own: computed(() => String(props.comment.author.id) === String(user.current?.id)),
    }
  },
})
