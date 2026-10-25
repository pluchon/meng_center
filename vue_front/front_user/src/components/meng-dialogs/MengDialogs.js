import {
  computed,
  defineAsyncComponent,
  defineComponent,
  inject,
  reactive,
  ref,
  toRefs,
  watch,
} from 'vue'
import {
  IconX,
  IconHeart,
  IconHeartFilled,
  IconLock,
  IconCheck,
  IconSparkles,
  IconChevronRight,
  IconChevronLeft,
  IconFiles,
  IconFileText,
  IconPhoto,
  IconTrash,
  IconPencil,
  IconPin,
  IconCalendar,
} from '@tabler/icons-vue'
import { CompanionContext } from '@/constants/meng'
import { companionApi, companionFile } from '@/api/companion'
import { useCompanionResource } from '@/composables/useCompanionResource'
import {
  companionRelationRoad,
  companionRelationWaves,
  companionRelationRainbow,
  companionRelationSun,
  companionRelationProgress,
} from '@/assets/images'
import { notifyFailure } from '@/composables/useFailureNotice'
import MengState from '@/components/meng-state/MengState.vue'
import DialogShell from '@/components/dialog-shell/DialogShell.vue'
export default defineComponent({
  name: 'MengDialogs',
  components: {
    DialogShell,
    MengToy: defineAsyncComponent(() => import('@/components/meng-toy/MengToy.vue')),
    MengState,
    MarkdownBody: defineAsyncComponent(() => import('@/components/markdown-body/MarkdownBody.vue')),
    IconX,
    IconHeart,
    IconHeartFilled,
    IconLock,
    IconCheck,
    IconSparkles,
    IconChevronRight,
    IconChevronLeft,
    IconFiles,
    IconTrash,
    IconPencil,
    IconPin,
    IconCalendar,
  },
  setup() {
    const context = inject(CompanionContext)
    const busy = ref(false),
      filter = ref(context.dialog === 'all' ? '' : context.modalCode),
      page = ref(1),
      selected = ref(null),
      imageError = ref(false)
    const memoryEdits = reactive({})
    const memory = useCompanionResource(
      (signal) => companionApi.memories(context.modalCode, { silent: true, signal }),
      [],
    )
    const works = useCompanionResource(
      (signal) =>
        companionApi.works(
          {
            character: filter.value || undefined,
            page: page.value,
            size: context.Companion.PAGE_SIZE,
          },
          { signal, silent: true },
        ),
      { records: [], total: 0 },
    )
    const preview = useCompanionResource((id, signal) =>
      companionApi.work(id, { silent: true, signal }),
    )
    const current = computed(() => preview.data.value)
    const bondCharacter = computed(() =>
      context.characters.data.find((c) => c.code === context.modalCode),
    )
    const shortName = computed(() => context.t('meng.' + context.modalCode + '.short'))
    const modalTheme = computed(() => context.modalCode.toLowerCase())
    // 公共对话框的语义色跟着角色走；首页的作品柜不属于某个角色，用紫色
    const tone = computed(() =>
      context.dialog === 'all' && !context.stageNotice
        ? 'violet'
        : { TSUNDERE: 'blue', GENKI: 'gold', NEKO: 'pink' }[context.modalCode],
    )
    const heading = computed(() =>
      context.stageNotice
        ? context.t('meng.stageUp', { name: shortName.value })
        : context.dialog === 'bond'
          ? context.t('meng.myBond', { name: shortName.value })
          : context.dialog === 'memory'
            ? context.t('meng.memoryTitle', { name: shortName.value })
            : context.dialog === 'all'
              ? context.t('meng.cabinet')
              : context.t('meng.together', { name: shortName.value }),
    )
    const headingIcon = computed(() =>
      context.dialog === 'bond' || context.dialog === 'memory' || context.stageNotice
        ? IconHeart
        : IconFiles,
    )
    const dialogWidth = computed(() =>
      context.stageNotice
        ? 'min(420px,calc(100vw - 32px))'
        : context.dialog === 'bond'
          ? 'min(900px,calc(100vw - 32px))'
          : context.dialog === 'memory'
            ? 'min(620px,calc(100vw - 32px))'
            : context.dialog === 'all'
              ? 'min(1060px,calc(100vw - 32px))'
              : 'min(860px,calc(100vw - 32px))',
    )
    async function selectWork(id) {
      selected.value = id
      imageError.value = false
      await preview.execute(id)
    }
    async function loadWorks() {
      const result = await works.execute()
      if (!result) return
      const target = context.dialogWorkId || result.records[0]?.id
      if (target) await selectWork(target)
      else {
        preview.data.value = null
        selected.value = null
      }
    }
    function setFilter(value) {
      filter.value = value
      context.dialogWorkId = null
      page.value = 1
      void loadWorks()
    }
    function changePage(delta) {
      page.value += delta
      context.dialogWorkId = null
      void loadWorks()
    }
    const loadMemory = () => memory.execute()
    const memoryGroups = computed(() =>
      context.Companion.MEMORY_KINDS.map((kind) => ({
        kind,
        records: memory.data.value.filter((item) => item.kind === kind),
      })).filter((group) => group.records.length),
    )
    async function editMemory(item, body) {
      if (busy.value) return
      busy.value = true
      try {
        await companionApi.editMemory(item.id, body)
        if (body.content != null) delete memoryEdits[item.id]
        await loadMemory()
        if (context.chatting) await context.loadSide()
      } catch (error) {
        notifyFailure(error)
      } finally {
        busy.value = false
      }
    }
    async function calendar(item) {
      try {
        const blob = await companionApi.calendar(item.id),
          url = URL.createObjectURL(blob)
        const anchor = document.createElement('a')
        anchor.href = url
        anchor.download = 'promise.ics'
        anchor.click()
        setTimeout(() => URL.revokeObjectURL(url), 1000)
      } catch (error) {
        notifyFailure(error)
      }
    }
    function close() {
      if (!busy.value) {
        context.dialog = ''
        context.stageNotice = null
      }
    }
    async function mutate(action, text) {
      await context.confirm(async () => {
        busy.value = true
        try {
          await action()
        } finally {
          busy.value = false
        }
      }, text)
    }
    function forget(id) {
      void mutate(async () => {
        await companionApi.deleteMemory(id)
        await loadMemory()
        if (context.chatting) await context.loadSide()
      })
    }
    function forgetAll() {
      void mutate(
        async () => {
          await companionApi.clearMemories(context.modalCode)
          await loadMemory()
          if (context.chatting) await context.loadSide()
        },
        context.t('meng.clearConfirm', { name: shortName.value }),
      )
    }
    function reset() {
      void mutate(async () => {
        await companionApi.reset(context.modalCode)
        await context.refreshCharacters()
        await loadMemory()
        if (context.chatting) await context.loadSide()
      }, context.t('meng.resetConfirm'))
    }
    function deleteWork() {
      if (current.value)
        void mutate(async () => {
          await companionApi.deleteWork(current.value.id)
          if (works.data.value.records.length === 1 && page.value > 1) page.value--
          await loadWorks()
          if (context.chatting) await context.loadSide()
        })
    }
    watch(
      () => context.dialog,
      (kind) => {
        if (kind === 'memory') void loadMemory()
        else if (kind === 'bond') {
          void loadMemory()
          void loadWorks()
        } else if (kind === 'works' || kind === 'all') {
          filter.value = kind === 'all' ? '' : context.modalCode
          void loadWorks()
        }
      },
      { immediate: true },
    )
    return {
      ...toRefs(context),
      works: reactive(works),
      memory: reactive(memory),
      memoryEdits,
      memoryGroups,
      editMemory,
      calendar,
      preview: reactive(preview),
      current,
      busy,
      filter,
      page,
      selected,
      imageError,
      bondCharacter,
      shortName,
      modalTheme,
      tone,
      heading,
      headingIcon,
      dialogWidth,
      loadingBond: computed(() => !bondCharacter.value),
      roadArt: companionRelationRoad,
      roadProgress: companionRelationProgress,
      roadWaves: companionRelationWaves,
      roadRainbow: companionRelationRainbow,
      roadSun: companionRelationSun,
      roadY: ['64.7059%', '35.2941%', '64.7059%', '35.2941%', '64.7059%'],
      IconFileText,
      IconPhoto,
      fileUrl: companionFile,
      selectWork,
      loadWorks,
      setFilter,
      changePage,
      loadMemory,
      close,
      forget,
      forgetAll,
      reset,
      deleteWork,
      retryImage: () => {
        imageError.value = false
      },
    }
  },
})
