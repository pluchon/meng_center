import { defineComponent, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { RouteName } from '@/router/names'
import { contentApi, contentImage } from '@/api/content'
import { userAvatar } from '@/assets/images'
import { useCommunity } from '@/composables/useCommunity'
import { usePostTime } from '@/composables/usePostTime'
import CommunityLayout from '@/layouts/community/CommunityLayout.vue'
import MarkdownBody from '@/components/markdown-body/MarkdownBody.vue'
import PageState from '@/components/page-state/PageState.vue'
export default defineComponent({
  name: 'PostDetailView',
  components: { CommunityLayout, MarkdownBody, PageState },
  setup() {
    const route = useRoute(),
      post = ref(null),
      loading = ref(false),
      error = ref(null),
      previewIndex = ref(null)
    let generation = 0
    async function load() {
      const current = ++generation
      loading.value = true
      error.value = null
      previewIndex.value = null
      try {
        const result = await contentApi.detail(route.params.id)
        if (current === generation) post.value = result
      } catch (e) {
        if (current === generation) error.value = e
      } finally {
        if (current === generation) loading.value = false
      }
    }
    watch(() => route.params.id, load, { immediate: true })
    return {
      RouteName,
      ...useCommunity(),
      post,
      loading,
      error,
      previewIndex,
      load,
      contentImage,
      userAvatar,
      relativeTime: usePostTime(),
    }
  },
})
