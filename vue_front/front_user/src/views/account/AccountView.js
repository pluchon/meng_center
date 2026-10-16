import {
  computed,
  defineAsyncComponent,
  defineComponent,
  onMounted,
  onUnmounted,
  ref,
  watch,
} from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { confirmDialog } from '@/composables/useConfirm'
import {
  IconAdjustmentsHorizontal as Setting,
  IconStar as Star,
  IconHeart,
  IconChevronLeft,
  IconCoin,
  IconMessageCircle as IconMessage,
  IconPencil,
  IconUserCheck,
  IconUserPlus,
  IconUsers,
} from '@tabler/icons-vue'
import { avatarUrl, userApi } from '@/api/user'
import { contentApi } from '@/api/content'
import { growthApi } from '@/api/growth'
import { useUserStore } from '@/stores/user'
import { useFavoriteStore } from '@/stores/favorites'
import { useRequestResource } from '@/composables/useRequestResource'
import { notifyFailure } from '@/composables/useFailureNotice'
import { RouteName } from '@/router/names'
import { Account, AccountPane } from '@/constants/account'
import {
  userAvatar,
  detailBubble,
  detailFlower,
  editorCirnoMascot,
  accountCameraIcon,
} from '@/assets/images'
import PageState from '@/components/page-state/PageState.vue'
import CommunityLayout from '@/layouts/community/CommunityLayout.vue'
import AccountFolders from '@/components/account-folders/AccountFolders.vue'
import AccountPosts from '@/components/account-posts/AccountPosts.vue'
import AccountSettings from '@/components/account-settings/AccountSettings.vue'
// 自己的个人中心和别人的主页共用这张页面：路由带用户编号就是别人的主页
export default defineComponent({
  name: 'AccountView',
  components: {
    PageState,
    CommunityLayout,
    AccountFolders,
    AccountPosts,
    AccountSettings,
    Setting,
    Star,
    IconHeart,
    IconChevronLeft,
    IconCoin,
    IconMessage,
    IconPencil,
    IconUserCheck,
    IconUserPlus,
    IconUsers,
    DarkVeil: defineAsyncComponent(() => import('@/components/dark-veil/DarkVeil.vue')),
    AccountDialogs: defineAsyncComponent(
      () => import('@/components/account-dialogs/AccountDialogs.vue'),
    ),
    FollowDialog: defineAsyncComponent(() => import('@/components/follow-dialog/FollowDialog.vue')),
    PointsDialog: defineAsyncComponent(() => import('@/components/points-dialog/PointsDialog.vue')),
  },
  setup() {
    const profileReview = useRequestResource((signal) => userApi.profileReview({ signal }), null)
    const { t } = useI18n(),
      route = useRoute(),
      router = useRouter(),
      user = useUserStore(),
      favorites = useFavoriteStore()
    const viewedId = computed(() => (route.params.id ? String(route.params.id) : null)),
      other = computed(() => Boolean(viewedId.value))
    const profileResource = useRequestResource(
        (signal) =>
          other.value
            ? userApi.publicProfile(viewedId.value, { signal })
            : userApi.me(false, { signal }),
        null,
      ),
      countsResource = useRequestResource(
        (signal) =>
          other.value
            ? contentApi.userCounts(viewedId.value, { signal })
            : contentApi.myCounts({ signal }),
        null,
      ),
      pointsResource = useRequestResource((signal) => growthApi.points({ signal }), null)
    onMounted(() => {
      if (!other.value) void profileReview.load()
    })
    const folders = ref([]),
      folderTotal = ref(0),
      folderRevision = ref(0),
      dialog = ref(null),
      followDialog = ref(null),
      pointsOpen = ref(false),
      followBusy = ref(false),
      uploading = ref(false),
      deleting = ref(false),
      loggingOut = ref(false)
    // 自己的页面有收藏和喜欢，别人的主页只有公开的收藏夹和发布的帖子
    const panes = computed(() =>
      other.value
        ? [AccountPane.FOLDERS, AccountPane.POSTS]
        : [AccountPane.FOLDERS, AccountPane.LIKES, AccountPane.SETTINGS],
    )
    const pane = computed(() =>
      panes.value.includes(route.query.pane) ? route.query.pane : AccountPane.FOLDERS,
    )
    const secondPane = computed(
      () => pane.value === AccountPane.LIKES || pane.value === AccountPane.POSTS,
    )
    const folderId = computed(() =>
      pane.value === AccountPane.FOLDERS && /^\d+$/.test(route.query.folder || '')
        ? String(route.query.folder)
        : null,
    )
    const folder = computed(() => folders.value.find((item) => String(item.id) === folderId.value))
    const page = computed(() => Math.max(1, Math.min(10000, parseInt(route.query.page) || 1)))
    const displayName = computed(
      () =>
        profileResource.state.data?.nickname ||
        profileResource.state.data?.username ||
        t('home.authorFallback'),
    )
    const pointsBalance = computed(() => pointsResource.state.data?.balance ?? 0)
    const sectionTitle = computed(() => {
      const total = countsResource.state.data
      if (pane.value === AccountPane.POSTS)
        return t('account.publishedCount', { count: total?.posts ?? 0 })
      if (pane.value === AccountPane.LIKES)
        return t('account.likedCount', { count: total?.likes ?? 0 })
      if (folder.value) return folderName(folder.value)
      return t(other.value ? 'account.otherFolders' : 'account.collections')
    })
    function go(query) {
      void router.push(
        other.value
          ? { name: RouteName.USER, params: { id: viewedId.value }, query }
          : { name: RouteName.ACCOUNT, query },
      )
    }
    function goPane(value) {
      go({ pane: value })
    }
    function openFolder(value) {
      go({ folder: value.id })
    }
    function goPage(value) {
      void router.push({ query: { ...route.query, page: value } })
    }
    function folderName(value) {
      return value.isDefault ? t('account.defaultFolder') : value.name
    }
    function openDialog(type) {
      dialog.value = type
    }
    function updatedFolder(value) {
      folders.value = folders.value.map((item) =>
        item.id === value.id ? { ...item, ...value } : item,
      )
      folderRevision.value++
      favorites.invalidate()
    }
    function updatedProfile(value) {
      profileResource.state.data = value
      user.setCurrent(value)
    }
    async function loadProfile() {
      if (!(await profileResource.load())) return
      if (!other.value) updatedProfile(profileResource.state.data)
    }
    async function loadFolders() {
      try {
        const params = { page: 1, size: other.value ? 20 : Account.FOLDERS_MAX }
        folders.value = (
          await (other.value
            ? contentApi.userFolders(viewedId.value, params)
            : contentApi.folders(params))
        ).records
      } catch (error) {
        notifyFailure(error)
      }
    }
    function dialogSaved(value) {
      if (dialog.value === 'folder') {
        folderRevision.value++
        favorites.invalidate()
        if (folderId.value) void loadFolders()
      } else if (value) updatedProfile(value)
      dialog.value = null
    }
    function closeFollowDialog() {
      followDialog.value = null
    }
    // 关掉积分弹窗后余额可能已经变了，重新取一次
    function closePoints() {
      pointsOpen.value = false
      void pointsResource.load()
    }
    async function toggleFollow() {
      if (followBusy.value || !profileResource.state.data) return
      followBusy.value = true
      const profile = profileResource.state.data,
        on = !profile.followed
      try {
        await userApi.follow(viewedId.value, on)
        profile.followed = on
        profile.followerCount = Math.max(0, profile.followerCount + (on ? 1 : -1))
      } catch (error) {
        notifyFailure(error)
      } finally {
        followBusy.value = false
      }
    }
    function message() {
      void router.push({ name: RouteName.MESSAGES, query: { with: viewedId.value } })
    }
    async function uploadAvatar(event) {
      const file = event.target.files[0]
      event.target.value = ''
      if (!file || uploading.value) return
      if (
        !['image/jpeg', 'image/png', 'image/webp'].includes(file.type) ||
        file.size > Account.AVATAR_BYTES
      ) {
        ElMessage.warning(t('account.avatarInvalid'))
        return
      }
      uploading.value = true
      try {
        await userApi.avatar(file)
        await loadProfile()
        ElMessage.success(t('account.avatarSaved'))
      } catch (error) {
        notifyFailure(error)
      } finally {
        uploading.value = false
      }
    }
    async function deleteFolder(value) {
      if (deleting.value) return
      deleting.value = true
      try {
        const confirmed = await confirmDialog({
          message: t('account.deleteConfirm'),
          title: t('account.deleteFolder'),
          confirmText: t('account.confirm'),
          cancelText: t('account.cancel'),
          danger: true,
        })
        if (!confirmed) return
        await contentApi.deleteFolder(value.id)
        favorites.invalidate()
        folderRevision.value++
        await loadFolders()
        void countsResource.load()
        goPane(AccountPane.FOLDERS)
        ElMessage.success(t('account.deleted'))
      } catch (error) {
        if (error !== 'cancel' && error !== 'close') notifyFailure(error)
      } finally {
        deleting.value = false
      }
    }
    async function logout() {
      if (loggingOut.value) return
      loggingOut.value = true
      try {
        const confirmed = await confirmDialog({
          message: t('account.logoutConfirm'),
          title: t('account.logout'),
          confirmText: t('account.confirm'),
          cancelText: t('account.cancel'),
        })
        if (!confirmed) return
        await user.logout()
        router.replace({ name: RouteName.LOGIN })
      } catch (error) {
        if (error !== 'cancel' && error !== 'close') notifyFailure(error)
      } finally {
        loggingOut.value = false
      }
    }
    // 打开自己的编号对应的主页，就是回到个人中心
    function leaveIfSelf() {
      if (other.value && viewedId.value === String(user.current?.id))
        void router.replace({ name: RouteName.ACCOUNT, query: route.query })
    }
    onMounted(() => {
      document.documentElement.classList.add('account-scrollbar-hidden')
      leaveIfSelf()
      void loadProfile()
      void countsResource.load()
      if (!other.value) void pointsResource.load()
      if (folderId.value) void loadFolders()
    })
    watch(viewedId, leaveIfSelf)
    onUnmounted(() => document.documentElement.classList.remove('account-scrollbar-hidden'))
    return {
      profileReview: profileReview.state,
      t,
      profile: profileResource.state,
      pointsBalance,
      loadCounts: countsResource.load,
      loadProfile,
      viewedId,
      other,
      pane,
      secondPane,
      folderId,
      folder,
      folders,
      folderTotal,
      page,
      displayName,
      sectionTitle,
      folderRevision,
      dialog,
      followDialog,
      pointsOpen,
      followBusy,
      uploading,
      deleting,
      loggingOut,
      goPane,
      openFolder,
      goPage,
      openDialog,
      updatedProfile,
      updatedFolder,
      dialogSaved,
      closeFollowDialog,
      closePoints,
      toggleFollow,
      message,
      uploadAvatar,
      deleteFolder,
      logout,
      avatarUrl,
      userAvatar,
      detailBubble,
      detailFlower,
      editorCirnoMascot,
      cameraIcon: accountCameraIcon,
      AccountPane,
      RouteName,
    }
  },
})
