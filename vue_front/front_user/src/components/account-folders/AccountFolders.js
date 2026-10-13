import { defineComponent, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { IconPlus as Plus } from '@tabler/icons-vue'
import { contentApi, contentImage } from '@/api/content'
import { Account } from '@/constants/account'
import { accountFolderCover } from '@/assets/images'
import { accountDate } from '@/utils/accountDate'
import PageState from '@/components/page-state/PageState.vue'
export default defineComponent({
  name: 'AccountFolders',
  components: { Plus, PageState },
  props: { revision: { type: Number, default: 0 } },
  emits: ['open', 'create', 'loaded', 'total'],
  setup(props, { emit }) {
    const { t } = useI18n(),
      rows = ref([]),
      total = ref(0),
      sentinel = ref(null),
      resource = reactive({ loading: false, error: null })
    let observer,
      controller,
      generation = 0
    async function loadMore(reset = false) {
      if (reset) {
        generation++
        controller?.abort()
        rows.value = []
        total.value = 0
        resource.loading = false
      }
      if (resource.loading || (!reset && rows.value.length && rows.value.length >= total.value))
        return
      controller = new AbortController()
      const current = generation
      resource.loading = true
      resource.error = null
      try {
        const value = await contentApi.folders(
          { page: Math.floor(rows.value.length / Account.SIZE) + 1, size: Account.SIZE },
          { signal: controller.signal },
        )
        if (current !== generation) return
        rows.value.push(...value.records)
        total.value = value.total
        emit('total', value.total)
        emit('loaded', [...rows.value])
      } catch (error) {
        if (current === generation && !error.cancelled) resource.error = error
      } finally {
        if (current === generation) resource.loading = false
      }
    }
    onMounted(async () => {
      await loadMore(true)
      await nextTick()
      observer = new IntersectionObserver(
        (entries) => {
          if (entries[0].isIntersecting && rows.value.length < total.value && !resource.error)
            void loadMore()
        },
        { rootMargin: '120px' },
      )
      if (sentinel.value) observer.observe(sentinel.value)
    })
    watch(
      () => props.revision,
      () => loadMore(true),
    )
    onUnmounted(() => {
      generation++
      controller?.abort()
      observer?.disconnect()
    })
    return {
      t,
      rows,
      total,
      resource,
      sentinel,
      loadMore: () => loadMore(),
      Account,
      contentImage,
      accountFolderCover,
      date: (value) => accountDate(value, t),
    }
  },
})
