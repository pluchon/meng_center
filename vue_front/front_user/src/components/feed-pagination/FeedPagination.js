import { defineComponent, computed } from 'vue'
import { feedPages } from '@/composables/feedPages'
import { useI18n } from 'vue-i18n'
export default defineComponent({
  name: 'FeedPagination',
  props: {
    page: { type: Number, required: true },
    totalPages: { type: Number, required: true },
    compact: { type: Boolean, default: false },
    showTop: { type: Boolean, default: true },
  },
  emits: ['page', 'top'],
  setup(props) {
    const pages = computed(() => feedPages(props.page, props.totalPages))
    const { t } = useI18n()
    return {
      t,
      pages,
    }
  },
})
