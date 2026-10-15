import { defineComponent, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { IconCamera as CoverCamera, IconPlus as Plus } from '@tabler/icons-vue'
import { contentApi, contentImage } from '@/api/content'
import { Account } from '@/constants/account'
import { accountFolderCover } from '@/assets/images'
import { accountDate } from '@/utils/accountDate'
import PageState from '@/components/page-state/PageState.vue'
import { notifyFailure } from '@/composables/useFailureNotice'
// 封面的大小和类型上限，和后端一致
const COVER_BYTES = 5 * 1024 * 1024
const COVER_TYPES = ['image/jpeg', 'image/png', 'image/webp']
export default defineComponent({
  name: 'AccountFolders',
  components: { CoverCamera, Plus, PageState },
  props: { revision: { type: Number, default: 0 } },
  emits: ['open', 'create', 'loaded', 'total'],
  setup(props, { emit }) {
    const { t } = useI18n(),
      rows = ref([]),
      total = ref(0),
      sentinel = ref(null),
      coverInput = ref(null),
      changing = ref(0),
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
    let target = null
    function pickCover(folder) {
      if (changing.value) return
      target = folder
      coverInput.value?.click()
    }
    // 换完只重取这一个收藏夹所在的那一页，把新封面地址换进原来的行里，列表不整体重载
    async function refreshRow(folder) {
      const index = rows.value.findIndex((row) => row.id === folder.id)
      if (index < 0) return
      const value = await contentApi.folders({
        page: Math.floor(index / Account.SIZE) + 1,
        size: Account.SIZE,
      })
      const fresh = value.records.find((row) => row.id === folder.id)
      if (fresh) rows.value[index] = fresh
    }
    async function coverSelected(event) {
      const file = event.target.files[0]
      event.target.value = ''
      const folder = target
      target = null
      if (!file || !folder) return
      if (!COVER_TYPES.includes(file.type) || file.size > COVER_BYTES) {
        ElMessage.warning(t('account.coverInvalid'))
        return
      }
      changing.value = folder.id
      try {
        const body = new FormData()
        body.append('cover', file)
        await contentApi.changeFolderCover(folder.id, body)
        await refreshRow(folder)
        ElMessage.success(t('account.coverChanged'))
      } catch (error) {
        notifyFailure(error)
      } finally {
        changing.value = 0
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
      coverInput,
      changing,
      pickCover,
      coverSelected,
      loadMore: () => loadMore(),
      Account,
      contentImage,
      accountFolderCover,
      date: (value) => accountDate(value, t),
    }
  },
})
