import { computed, defineComponent } from 'vue'
import { commentSegments } from '@/constants/comments'
export default defineComponent({
  name: 'CommentBody',
  props: { content: { type: String, default: '' } },
  setup(props) {
    return { segments: computed(() => commentSegments(props.content)) }
  },
})
