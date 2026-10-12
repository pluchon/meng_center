import { computed, defineComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { Categories, PostSort } from '@/constants/content'
export default defineComponent({
  name: 'FeedFilters',
  props: { category: { type: String, default: undefined }, sort: { type: String, required: true } },
  emits: ['change'],
  setup(props) {
    return { ...useI18n(), Categories, PostSort, sortValue: computed(() => props.sort) }
  },
})
