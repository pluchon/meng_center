import {
  messageBackIcon,
  navMessageIcon,
  messageChevronIcon,
  navGrowthIcon,
  messageHeartIcon,
  messageShieldIcon,
} from '@/assets/images'
import { computed, defineComponent, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { RouteName } from '@/router/names'
import { useUserStore } from '@/stores/user'
import { useImStore } from '@/stores/im'
import { MessageTab, MessageTabs, NotificationChannel, NoticeType, Im } from '@/constants/im'
import { imApi } from '@/api/im'
import { usePostTime } from '@/composables/usePostTime'
import { notifyFailure } from '@/composables/useFailureNotice'
import {
  userAvatar,
  communitySparkle,
  authCodeIcon,
  editorCirnoMascot,
} from '@/assets/images'
import MessageChat from '@/components/message-chat/MessageChat.vue'
import MessageNotices from '@/components/message-notices/MessageNotices.vue'
import MessageAvatar from '@/components/message-avatar/MessageAvatar.vue'
import PixelBlast from '@/components/pixel-blast/PixelBlast.vue'
export default defineComponent({
  name: 'MessagesView',
  components: {
    PixelBlast,
    MessageAvatar,

    MessageChat,
    MessageNotices,
  },
  setup() {
    const { t } = useI18n(),
      route = useRoute(),
      router = useRouter(),
      user = useUserStore(),
      im = useImStore()
    const tab = computed(() =>
      MessageTabs.includes(route.query.tab) ? route.query.tab : MessageTab.CHAT,
    )
    // 四个页签各自的图标
    const tabIcons = {
      chat: navMessageIcon,
      comment: authCodeIcon,
      like: messageHeartIcon,
      system: messageShieldIcon,
    }
    // 列表在滚动时先停掉背景动画，滚动更跟手
    const pixel = ref(null)
    const pausePixels = () => pixel.value?.hold(220)
    const availableGroups = computed(() =>
      tab.value === MessageTab.SYSTEM ? ['GROWTH', 'REPORT', 'APPROVAL'] : ['POST'],
    )
    const group = computed(() =>
      availableGroups.value.includes(route.query.group)
        ? route.query.group
        : availableGroups.value[0],
    )
    const page = computed(() =>
      /^\d+$/.test(route.query.page || '') &&
      Number.isSafeInteger(Number(route.query.page)) &&
      Number(route.query.page) > 0
        ? Number(route.query.page)
        : 1,
    )
    const groups = ref([]),
      groupsLoading = ref(false),
      groupsError = ref(''),
      records = ref([]),
      total = ref(0),
      loading = ref(false),
      error = ref(''),
      presenceOpen = ref(false),
      changingPresence = ref(false)
    let groupsController,
      noticesController,
      disposed = false,
      refreshing = null
    function switchTab(value) {
      void router.push({
        name: RouteName.MESSAGES,
        query: {
          tab: value,
          ...(value === MessageTab.CHAT && im.current ? { with: String(im.current.peer.id) } : {}),
        },
      })
    }
    function tabKey(event, value) {
      const index = MessageTabs.indexOf(value)
      const next =
        event.key === 'ArrowRight'
          ? MessageTabs[(index + 1) % MessageTabs.length]
          : event.key === 'ArrowLeft'
            ? MessageTabs[(index + MessageTabs.length - 1) % MessageTabs.length]
            : event.key === 'Home'
              ? MessageTabs[0]
              : event.key === 'End'
                ? MessageTabs.at(-1)
                : null
      if (!next) return
      event.preventDefault()
      switchTab(next)
      document.getElementById('message-tab-' + next)?.focus()
    }
    function selectPeer(id) {
      void router.push({
        name: RouteName.MESSAGES,
        query: { tab: MessageTab.CHAT, with: String(id) },
      })
    }
    function selectGroup(value) {
      void router.push({ name: RouteName.MESSAGES, query: { tab: tab.value, group: value } })
    }
    function turn(value) {
      void router.push({
        name: RouteName.MESSAGES,
        query: { tab: tab.value, group: group.value, page: String(value) },
      })
    }
    async function loadGroups() {
      if (tab.value === MessageTab.CHAT) return
      groupsController?.abort()
      const controller = new AbortController(),
        channel = NotificationChannel[tab.value]
      groupsController = controller
      groupsLoading.value = true
      groupsError.value = ''
      try {
        const result = await imApi.groups(channel, { signal: controller.signal, silent: true })
        if (!controller.signal.aborted && !disposed) groups.value = result
      } catch (failure) {
        if (!controller.signal.aborted && !disposed) groupsError.value = failure.message
      } finally {
        if (groupsController === controller) groupsLoading.value = false
      }
    }
    async function loadNotices(clear = false) {
      if (tab.value === MessageTab.CHAT) return
      noticesController?.abort()
      const controller = new AbortController(),
        currentTab = tab.value,
        currentGroup = group.value
      noticesController = controller
      const params = {
        channel: NotificationChannel[currentTab],
        ...(currentTab === MessageTab.SYSTEM
          ? { category: currentGroup }
          : { source: currentGroup }),
        page: page.value,
        size: Im.NOTICE_SIZE,
      }
      if (clear) records.value = []
      loading.value = true
      error.value = ''
      try {
        const result = await imApi.notices(params, { signal: controller.signal, silent: true })
        if (controller.signal.aborted || disposed) return
        records.value = result.records
        total.value = result.total
        await imApi.readNotices({
          channel: params.channel,
          source: params.source,
          category: params.category,
        })
        if (!controller.signal.aborted && !disposed) void loadGroups()
      } catch (failure) {
        if (!controller.signal.aborted && !disposed) error.value = failure.message
      } finally {
        if (noticesController === controller) loading.value = false
      }
    }
    const stopRoute = watch(
      [tab, group, page],
      (values, previous) => {
        im.setReading(tab.value === MessageTab.CHAT)
        if (tab.value === MessageTab.CHAT) {
          groupsController?.abort()
          noticesController?.abort()
          void im.loadConversations()
        } else {
          void loadGroups()
          void loadNotices(!previous || values[0] !== previous[0] || values[1] !== previous[1])
        }
      },
      { immediate: true },
    )
    const stopPeer = watch(
      () => [tab.value, route.query.with],
      ([value, peer]) => {
        if (
          value === MessageTab.CHAT &&
          /^\d+$/.test(peer || '') &&
          Number.isSafeInteger(Number(peer)) &&
          Number(peer) > 0 &&
          Number(peer) !== im.current?.peer.id
        )
          void im.open(Number(peer))
      },
      { immediate: true },
    )
    const stopDefault = watch(
      [() => im.conversations[0]?.peer.id, tab, () => route.query.with],
      ([peer, value, requested]) => {
        const explicit = Number.isSafeInteger(Number(requested)) && Number(requested) > 0
        if (peer && value === MessageTab.CHAT && !explicit && !im.current && !im.opening)
          void router.replace({
            name: RouteName.MESSAGES,
            query: { tab: MessageTab.CHAT, with: String(peer) },
          })
      },
      { immediate: true },
    )
    const stopRevision = watch(
      () => im.revision,
      () => {
        clearTimeout(refreshing)
        refreshing = setTimeout(() => {
          if (tab.value !== MessageTab.CHAT) {
            void loadGroups()
            void loadNotices()
          }
        }, Im.REFRESH_DELAY)
      },
    )
    async function setPresence(hidden) {
      if (changingPresence.value) return
      changingPresence.value = true
      try {
        await im.setHidden(hidden)
        presenceOpen.value = false
      } catch (failure) {
        notifyFailure(failure)
      } finally {
        changingPresence.value = false
      }
    }
    // 会话列表里的预览：表情记号显示成「[表情]」
    function previewText(value) {
      return (value || '').replace(/\[表情:[^\s[\]]{1,12}\]/gu, t('im.emotionPreview'))
    }
    function groupRow(key) {
      return groups.value.find((item) => item.key === key)
    }
    function summary(item) {
      if (!item?.latest) return t('im.noPreview')
      const notice = item.latest.notice
      if (notice.type === NoticeType.LEVEL_CHANGED)
        return t('im.toLevel', { level: notice.level, name: '' })
      return t('im.action.' + notice.type)
    }
    const nickname = (item) => item?.latest?.actors[0]?.nickname
    const escapePresence = (event) => {
      if (event.key === 'Escape') presenceOpen.value = false
    }
    onMounted(() => {
      document.documentElement.classList.add('messages-scrollbar-hidden')
      document.addEventListener('keydown', escapePresence)
    })
    onUnmounted(() => {
      disposed = true
      stopRoute()
      stopPeer()
      stopDefault()
      stopRevision()
      groupsController?.abort()
      noticesController?.abort()
      clearTimeout(refreshing)
      im.close()
      document.removeEventListener('keydown', escapePresence)
      document.documentElement.classList.remove('messages-scrollbar-hidden')
    })
    return {
      messageBackIcon,
      navMessageIcon,
      messageChevronIcon,
      navGrowthIcon,
      messageHeartIcon,
      messageShieldIcon,
      tabIcons,
      editorCirnoMascot,
      pixel,
      pausePixels,
      t,
      route,
      user,
      im,
      tab,
      group,
      page,
      groups,
      groupsLoading,
      groupsError,
      records,
      total,
      loading,
      error,
      presenceOpen,
      changingPresence,
      availableGroups,
      totalPages: computed(() => Math.max(1, Math.ceil(total.value / Im.NOTICE_SIZE))),
      switchTab,
      tabKey,
      selectPeer,
      selectGroup,
      turn,
      loadGroups,
      loadNotices,
      setPresence,
      previewText,
      groupRow,
      summary,
      nickname,
      relativeTime: usePostTime(),
      RouteName,
      MessageTab,
      MessageTabs,
      userAvatar,
      communitySparkle,
    }
  },
})
