import { computed, defineComponent, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import {
  IconUsers,
  IconUserPlus,
  IconUserCheck,
} from '@tabler/icons-vue'
import { userApi } from '@/api/user'
import { useRequestResource } from '@/composables/useRequestResource'
import { notifyFailure } from '@/composables/useFailureNotice'
import { Account } from '@/constants/account'
import { RouteName } from '@/router/names'
import DialogShell from '@/components/dialog-shell/DialogShell.vue'
import FeedPagination from '@/components/feed-pagination/FeedPagination.vue'
import MessageAvatar from '@/components/message-avatar/MessageAvatar.vue'
import PageState from '@/components/page-state/PageState.vue'
// 关注或粉丝列表的弹窗（两个入口各开各的）：每页几个人、行尾的关注按钮，只有一页也显示翻页器
export default defineComponent({
  name: 'FollowDialog',
  components: { DialogShell, FeedPagination, MessageAvatar, PageState },
  props: {
    userId: { type: [String, Number], required: true },
    // following 或 followers
    kind: { type: String, required: true },
    count: { type: Number, default: 0 },
  },
  emits: ['close', 'changed'],
  setup(props, { emit }) {
    const { t } = useI18n(),
      router = useRouter()
    const following = props.kind === 'following'
    const page = ref(1),
      pending = reactive({})
    const { state: resource, load } = useRequestResource(
      (signal) =>
        (following ? userApi.following : userApi.followers)(
          props.userId,
          { page: page.value, size: Account.FOLLOW_SIZE },
          { signal },
        ),
      { records: [], total: 0 },
    )
    const totalPages = computed(() =>
      Math.max(1, Math.ceil(resource.data.total / Account.FOLLOW_SIZE)),
    )
    function goPage(value) {
      page.value = value
    }
    // 互相关注、已关注、他关注了我还没回关、都还没关注，四种状态四种按钮
    const actionLabel = (row) =>
      row.followed
        ? row.followsMe
          ? 'account.mutual'
          : 'account.following'
        : row.followsMe
          ? 'account.followBack'
          : 'account.follow'
    const actionIcon = (row) =>
      row.followed ? (row.followsMe ? IconUsers : IconUserCheck) : IconUserPlus
    const actionTone = (row) => (row.followed ? (row.followsMe ? 'mutual' : 'followed') : 'primary')
    async function toggle(row) {
      if (pending[row.id]) return
      pending[row.id] = true
      const on = !row.followed
      try {
        await userApi.follow(row.id, on)
        row.followed = on
        emit('changed')
      } catch (error) {
        notifyFailure(error)
      } finally {
        delete pending[row.id]
      }
    }
    function openUser(row) {
      emit('close')
      void router.push(row.self ? { name: RouteName.ACCOUNT } : { name: RouteName.USER, params: { id: row.id } })
    }
    watch(page, () => load())
    onMounted(load)
    return {
      t,
      following,
      page,
      pending,
      resource,
      load,
      totalPages,
      goPage,
      actionLabel,
      actionIcon,
      actionTone,
      toggle,
      openUser,
      IconUsers,
    }
  },
})
