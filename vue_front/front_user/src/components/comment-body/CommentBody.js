import { computed, defineComponent } from 'vue'
import { commentSegments } from '@/constants/comments'
export default defineComponent({
  name: 'CommentBody',
  props: { content: { type: String, default: '' } },
  setup(props) {
    const segments = computed(() => commentSegments(props.content))
    return {
      texts: computed(() => segments.value.filter((item) => !item.image)),
      images: computed(() => segments.value.filter((item) => item.image)),
    }
  },
})
