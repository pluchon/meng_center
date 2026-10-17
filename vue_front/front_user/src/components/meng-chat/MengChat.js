import { defineAsyncComponent, computed, defineComponent, inject, toRefs } from 'vue'
import { useRouter } from 'vue-router'
import {
  IconSnowflake,
  IconSun,
  IconCat,
  IconPlus,
  IconMessages,
  IconMessageCircle,
  IconX,
  IconHeart,
  IconLayoutSidebarRightExpand,
  IconLayoutSidebarRightCollapse,
  IconChevronLeft,
  IconChevronRight,
  IconFileText,
  IconLoader2,
  IconHeartHandshake,
  IconPlant,
  IconSend,
  IconCalendar,
  IconClock,
  IconFolder,
  IconPhoto,
  IconSparkles,
} from '@tabler/icons-vue'
import { CompanionContext } from '@/constants/meng'
import { Emotions } from '@/constants/comments'
import { companionHistoryEarlierIcon, companionReplyDotsArt } from '@/assets/images'
import MengState from '@/components/meng-state/MengState.vue'
import { notifyFailure } from '@/composables/useFailureNotice'
export default defineComponent({
  name: 'MengChat',
  components: {
    MengState,
    MengWorkCard: defineAsyncComponent(
      () => import('@/components/meng-work-card/MengWorkCard.vue'),
    ),
    IconPlus,
    IconMessages,
    IconMessageCircle,
    IconX,
    IconHeart,
    IconLayoutSidebarRightExpand,
    IconLayoutSidebarRightCollapse,
    IconChevronLeft,
    IconChevronRight,
    IconFileText,
    IconLoader2,
    IconHeartHandshake,
    IconPlant,
    IconSend,
    IconCalendar,
    IconClock,
    IconFolder,
    IconSparkles,
  },
  setup() {
    const context = inject(CompanionContext),
      router = useRouter()
    return {
      ...toRefs(context),
      watermarkIcon: computed(
        () => ({ TSUNDERE: IconSnowflake, GENKI: IconSun, NEKO: IconCat })[context.code],
      ),
      IconFileText,
      IconPhoto,
      earlierIcon: companionHistoryEarlierIcon,
      replyDots: companionReplyDotsArt,
      stickerImage: (name) => Emotions.find((item) => item.name === name)?.image,
      setLog: (element) => {
        context.log = element
      },
      isNewDay: (message, previous) =>
        context.day(message.createTime) !== context.day(previous?.createTime),
      safeHelpUrl: (line) =>
        /^https:\/\//.test(line.url || '')
          ? line.url
          : /^[-+\d ()]+$/.test(line.phone || '')
            ? 'tel:' + line.phone
            : undefined,
      retryHistory: async () => {
        if (context.loadError?.status === 404)
          await router.replace({
            name: context.RouteName.MENG,
            params: { character: context.code.toLowerCase() },
          })
        context.sessionId = null
        return context.start()
      },
      moreTopics: async () => {
        try {
          await context.loadSessions(true)
        } catch (e) {
          notifyFailure(e)
        }
      },
    }
  },
})
