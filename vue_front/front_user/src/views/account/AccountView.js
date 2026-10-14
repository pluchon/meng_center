import { computed, defineAsyncComponent, defineComponent, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { confirmDialog } from '@/composables/useConfirm'
import {
  IconCamera as CameraIcon,
  IconAdjustmentsHorizontal as Setting,
  IconStar as Star,
  IconHeart,
  IconChevronLeft,
} from '@tabler/icons-vue'
import { avatarUrl, userApi } from '@/api/user'
import { contentApi } from '@/api/content'
import { useUserStore } from '@/stores/user'
import { useFavoriteStore } from '@/stores/favorites'
import { useRequestResource } from '@/composables/useRequestResource'
import { notifyFailure } from '@/composables/useFailureNotice'
import { RouteName } from '@/router/names'
import { Account, AccountPane } from '@/constants/account'
import { userAvatar, detailBubble, detailFlower } from '@/assets/images'
import PageState from '@/components/page-state/PageState.vue'
import CommunityLayout from '@/layouts/community/CommunityLayout.vue'
import AccountFolders from '@/components/account-folders/AccountFolders.vue'
import AccountPosts from '@/components/account-posts/AccountPosts.vue'
import AccountSettings from '@/components/account-settings/AccountSettings.vue'
export default defineComponent({
  name: 'AccountView',
  components: {
    PageState,
    CommunityLayout,
    AccountFolders,
    AccountPosts,
    AccountSettings,
    CameraIcon,
    Setting,
    Star,
    IconHeart,
    IconChevronLeft,
    DarkVeil: defineAsyncComponent(() => import('@/components/dark-veil/DarkVeil.vue')),
    AccountDialogs: defineAsyncComponent(
      () => import('@/components/account-dialogs/AccountDialogs.vue'),
    ),
  },
  setup() {
    const { t } = useI18n(),
      route = useRoute(),
      router = useRouter(),
      user = useUserStore(),
      favorites = useFavoriteStore()
    const profileResource = useRequestResource((signal) => userApi.me(false, { signal }), null),
      countsResource = useRequestResource((signal) => contentApi.myCounts({ signal }), null)
    const folders = ref([]),
      folderTotal = ref(0),
      folderRevision = ref(0),
      dialog = ref(null),
      uploading = ref(false),
      deleting = ref(false),
      loggingOut = ref(false)
    const pane = computed(() =>
      Object.values(AccountPane).includes(route.query.pane)
        ? route.query.pane
        : AccountPane.FOLDERS,
    )
    const folderId = computed(() =>
      pane.value === AccountPane.FOLDERS && /^\d+$/.test(route.query.folder || '')
        ? String(route.query.folder)
        : null,
    )
    const folder = computed(() => folders.value.find((item) => String(item.id) === folderId.value))
    const page = computed(() => Math.max(1, Math.min(10000, parseInt(route.query.page) || 1)))
    const joinedDays = computed(
      () =>
        Math.max(
          1,
          Math.floor(
            (Date.now() -
              Date.parse(
                (
                  profileResource.state.data?.createdAt ||
                  profileResource.state.data?.createTime ||
                  ''
                ).replace(/Z$/, '') + 'Z',
              )) /
              86400000,
          ) + 1,
        ) || 1,
    )
    function goPane(value) {
      router.push({ name: RouteName.ACCOUNT, query: { pane: value } })
    }
    function openFolder(value) {
      router.push({ name: RouteName.ACCOUNT, query: { folder: value.id } })
    }
    function goPage(value) {
      router.push({ query: { ...route.query, page: value } })
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
      if (await profileResource.load()) updatedProfile(profileResource.state.data)
    }
    async function loadFolders() {
      try {
        folders.value = (await contentApi.folders({ page: 1, size: Account.FOLDERS_MAX })).records
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
    onMounted(() => {
      document.documentElement.classList.add('account-scrollbar-hidden')
      void loadProfile()
      void countsResource.load()
      if (folderId.value) void loadFolders()
    })
    onUnmounted(() => document.documentElement.classList.remove('account-scrollbar-hidden'))
    return {
      t,
      profile: profileResource.state,
      counts: countsResource.state,
      loadCounts: countsResource.load,
      loadProfile,
      pane,
      folderId,
      folder,
      folders,
      folderTotal,
      page,
      joinedDays,
      folderRevision,
      dialog,
      uploading,
      deleting,
      loggingOut,
      goPane,
      openFolder,
      goPage,
      folderName,
      openDialog,
      updatedProfile,
      updatedFolder,
      dialogSaved,
      uploadAvatar,
      deleteFolder,
      logout,
      avatarUrl,
      userAvatar,
      detailBubble,
      detailFlower,
      AccountPane,
      RouteName,
    }
  },
})
