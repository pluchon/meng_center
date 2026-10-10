import { defineComponent, reactive, ref } from 'vue'
import {
  Picture as PictureIcon,
  Location,
  View as ViewIcon,
  Star,
  ChatDotRound,
  CollectionTag,
} from '@element-plus/icons-vue'
import { contentApi, contentImage } from '@/api/content'
import { useCommunity } from '@/composables/useCommunity'
import { usePostTime } from '@/composables/usePostTime'
import { notifyFailure } from '@/composables/useFailureNotice'
import { userAvatar } from '@/assets/images'
import { RouteName } from '@/router/names'
export default defineComponent({
  name: 'PostCard',
  components: { PictureIcon, Location, ViewIcon, Star, ChatDotRound, CollectionTag },
  props: { post: { type: Object, required: true } },
  emits: ['change'],
  setup(props, { emit }) {
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
      relativeTime: usePostTime(),
      contentImage,
      userAvatar,
      RouteName,
    }
  },
})
