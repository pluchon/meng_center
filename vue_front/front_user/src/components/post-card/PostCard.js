import { computed, defineComponent, reactive, ref } from 'vue'
import {
  Picture as PictureIcon,
  Clock,
  View as ViewIcon,
  Star,
  ChatDotRound,
  CollectionTag,
} from '@element-plus/icons-vue'
import { contentApi, contentImage } from '@/api/content'
import { useRoute, useRouter } from 'vue-router'
import { useCommunity } from '@/composables/useCommunity'
import { usePostTime } from '@/composables/usePostTime'
import { notifyFailure } from '@/composables/useFailureNotice'
import { userAvatar, regionSilhouettes } from '@/assets/images'
import { postRegion } from '@/utils/postRegion'
import HighlightText from '@/components/highlight-text/HighlightText.vue'
import { RouteName } from '@/router/names'
import { useFavoriteStore } from '@/stores/favorites'
import { avatarUrl } from '@/api/user'
export default defineComponent({
  name: 'PostCard',
  components: {
    HighlightText,
    PictureIcon,
    Clock,
    ViewIcon,
    Star,
    ChatDotRound,
    CollectionTag,
  },
  props: { post: { type: Object, required: true } },
  emits: ['change'],
  setup(props, { emit }) {
    const region = computed(() => postRegion(props.post.ipRegion))
    const route = useRoute()
    const router = useRouter()
    const homeQuery = computed(() =>
      route.name === RouteName.SEARCH ? { ...route.query, from: 'search' } : route.query,
    )
    const detailLink = computed(() => ({
      name: RouteName.POST_DETAIL,
      params: { id: props.post.id },
      query: homeQuery.value,
    }))
    function openDetail() {
      return router.push(detailLink.value)
    }
    function preloadDetail() {
      if (navigator.connection?.saveData || /2g/.test(navigator.connection?.effectiveType || ''))
        return
      void import('@/views/post/detail/PostDetailView.vue').catch(() => undefined)
    }
    function openCard(event) {
      if (
        event.defaultPrevented ||
        event.button !== 0 ||
        !event.currentTarget.contains(event.target) ||
        event.target.closest('a, button, input, textarea, select, [contenteditable]') ||
        window.getSelection()?.toString()
      )
        return
      openDetail()
    }
    const pending = reactive({ like: false, favorite: false }),
      previewIndex = ref(null)
    function preview(index) {
      previewIndex.value = index
    }
    async function toggle(kind) {
      if (pending[kind]) return
      const field = kind === 'like' ? 'liked' : 'favorited',
        on = !props.post[field]
      pending[kind] = true
      if (kind === 'favorite') {
        try {
          await useFavoriteStore().manage(props.post, ({ favorited, favoriteCount }) =>
            emit('change', {
              id: props.post.id,
              field: 'favorited',
              value: favorited,
              delta: favoriteCount - props.post.favoriteCount,
            }),
          )
        } catch (error) {
          notifyFailure(error)
        } finally {
          pending[kind] = false
        }
        return
      }
      emit('change', {
        id: props.post.id,
        field,
        value: on,
        delta: on ? 1 : -1,
      })
      try {
        await contentApi[kind](props.post.id, on)
      } catch (e) {
        emit('change', {
          id: props.post.id,
          field,
          value: !on,
          delta: on ? -1 : 1,
        })
        notifyFailure(e)
      } finally {
        pending[kind] = false
      }
    }
    return {
      ...useCommunity(),
      pending,
      previewIndex,
      preview,
      toggle,
      openCard,
      preloadDetail,
      openDetail,
      detailLink,
      relativeTime: usePostTime(),
      contentImage,
      userAvatar,
      region,
      regionSilhouettes,
      avatarUrl,
    }
  },
})
