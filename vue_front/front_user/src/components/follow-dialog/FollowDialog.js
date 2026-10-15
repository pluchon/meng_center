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
// 关注和粉丝列表的弹窗：两个页签、每页几个人、行尾的关注按钮
export default defineComponent({
  name: 'FollowDialog',
  components: { DialogShell, FeedPagination, MessageAvatar, PageState },
  props: {
    userId: { type: [String, Number], required: true },
    initial: { type: String, default: 'following' },
    counts: { type: Object, required: true },
  },
  emits: ['close', 'changed'],
  setup(props, { emit }) {
    const { t } = useI18n(),
      router = useRouter()
    const tab = ref(props.initial),
      page = ref(1),
      pending = reactive({})
    const { state: resource, load } = useRequestResource(
      (signal) =>
        (tab.value === 'following' ? userApi.following : userApi.followers)(
          props.userId,
          { page: page.value, size: Account.FOLLOW_SIZE },
          { signal },
        ),
      { records: [], total: 0 },
    )
    const tabs = computed(() => [
      { key: 'following', label: 'account.followCount', tone: 'blue', count: props.counts.following },
      { key: 'followers', label: 'account.fansCount', tone: 'pink', count: props.counts.followers },
    ])
    const totalPages = computed(() => Math.ceil(resource.data.total / Account.FOLLOW_SIZE))
    function switchTab(value) {
      if (tab.value === value) return
      tab.value = value
      page.value = 1
    }
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
    watch([tab, page], () => load())
    onMounted(load)
    return {
      t,
      tab,
      tabs,
      page,
      pending,
      resource,
      load,
      totalPages,
      switchTab,
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
