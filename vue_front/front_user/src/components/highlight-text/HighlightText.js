import { computed, defineComponent } from 'vue'
export default defineComponent({
  name: 'HighlightText',
  props: { text: { type: String, default: '' }, segments: { type: Array, default: () => [] } },
  setup(props) {
    return {
      parts: computed(() =>
        props.segments.length ? props.segments : [{ text: props.text, matched: false }],
      ),
    }
  },
})
