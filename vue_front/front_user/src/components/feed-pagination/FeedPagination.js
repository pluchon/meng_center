import { defineComponent, computed } from 'vue'
import { feedPages } from '@/composables/feedPages'
import { useCommunity } from '@/composables/useCommunity'
export default defineComponent({
  name: 'FeedPagination',
  props: { page: { type: Number, required: true }, totalPages: { type: Number, required: true } },
  emits: ['page'],
  setup(props) {
    const pages = computed(() => feedPages(props.page, props.totalPages))
    return {
      ...useCommunity(),
      pages,
      top: () =>
        window.scrollTo({
          top: 0,
          behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth',
        }),
    }
  },
})
