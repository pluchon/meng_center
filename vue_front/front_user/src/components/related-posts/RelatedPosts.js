import { computed, defineComponent, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { contentApi } from '@/api/content'
import { RouteName } from '@/router/names'
import { useRequestResource } from '@/composables/useRequestResource'
import { notifyFailure } from '@/composables/useFailureNotice'
import PageState from '@/components/page-state/PageState.vue'

// 每次展示几篇，总数由后端限定（最多 50 篇）
const PAGE_SIZE = 5

export default defineComponent({
  name: 'RelatedPosts',
  components: { PageState },
  props: { postId: { type: [Number, String], required: true } },
  setup(props) {
    const extra = ref([]),
      nextPage = ref(2),
      loadingMore = ref(false)
    const resource = useRequestResource(
      (signal) =>
        contentApi.related(props.postId, { page: 1, size: PAGE_SIZE }, { signal, silent: true }),
      null,
    )
    const records = computed(() => [...(resource.state.data?.records ?? []), ...extra.value])
    const total = computed(() => resource.state.data?.total ?? 0)
    async function more() {
      if (loadingMore.value) return
      loadingMore.value = true
      const id = props.postId
      try {
        const result = await contentApi.related(id, { page: nextPage.value, size: PAGE_SIZE })
        if (id !== props.postId) return
        extra.value = [...extra.value, ...result.records]
        nextPage.value++
      } catch (error) {
        if (!error.cancelled) notifyFailure(error)
      } finally {
        loadingMore.value = false
      }
    }
    watch(
      () => props.postId,
      () => {
        resource.clear()
        extra.value = []
        nextPage.value = 2
        void resource.load()
      },
      { immediate: true },
    )
    return {
      ...useI18n(),
      RouteName,
      resource: resource.state,
      load: resource.load,
      records,
      total,
      loadingMore,
      more,
    }
  },
})
