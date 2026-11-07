import { computed, defineComponent, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { contentApi, contentImage } from '@/api/content'
import { RouteName } from '@/router/names'
import { useRequestResource } from '@/composables/useRequestResource'
import { notifyFailure } from '@/composables/useFailureNotice'
import { IconChevronLeft, IconChevronRight, IconRefresh, IconSparkles } from '@tabler/icons-vue'
import { avatarUrl } from '@/api/user'
import { accountScenes, userAvatar } from '@/assets/images'
import CategoryTag from '@/components/category-tag/CategoryTag.vue'
import PageState from '@/components/page-state/PageState.vue'

// 每组几篇，总数由后端限定（最多 50 篇）
const PAGE_SIZE = 5

export default defineComponent({
  name: 'RelatedPosts',
  components: { IconChevronLeft, IconChevronRight, IconRefresh, IconSparkles, CategoryTag, PageState },
  props: { postId: { type: [Number, String], required: true } },
  setup(props) {
    // 换一换取下一组替换当前这组；换过的组都留着，左右箭头翻回去看
    const extra = ref([]),
      index = ref(0),
      loadingMore = ref(false)
    const resource = useRequestResource(
      (signal) =>
        contentApi.related(props.postId, { page: 1, size: PAGE_SIZE }, { signal, silent: true }),
      null,
    )
    const pages = computed(() => {
      const first = resource.state.data?.records ?? []
      return first.length ? [first, ...extra.value] : []
    })
    const records = computed(() => pages.value[index.value] ?? [])
    const total = computed(() => resource.state.data?.total ?? 0)
    const hasMore = computed(() => pages.value.length < Math.ceil(total.value / PAGE_SIZE))
    async function more() {
      if (loadingMore.value || !hasMore.value) return
      loadingMore.value = true
      const id = props.postId
      try {
        const result = await contentApi.related(id, {
          page: pages.value.length + 1,
          size: PAGE_SIZE,
        })
        if (id !== props.postId || !result.records.length) return
        extra.value = [...extra.value, result.records]
        index.value = pages.value.length - 1
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
        index.value = 0
        void resource.load()
      },
      { immediate: true },
    )
    return {
      ...useI18n(),
      RouteName,
      contentImage,
      avatarUrl,
      accountScenes,
      userAvatar,
      resource: resource.state,
      load: resource.load,
      pages,
      index,
      records,
      hasMore,
      loadingMore,
      more,
    }
  },
})
