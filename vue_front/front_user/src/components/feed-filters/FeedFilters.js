import { computed, defineComponent, onUnmounted, ref } from 'vue'
import { useSlider } from '@/composables/useSlider'
import { useI18n } from 'vue-i18n'
import { Categories, PostCategory, PostSort } from '@/constants/content'
import {
  categoryAllIcon,
  categoryAnimeIcon,
  navCreateIcon,
  categoryGameIcon,
  categoryTechIcon,
  categoryDailyIcon,
} from '@/assets/images'
const HOVER_SELECT_MS = 400
const categoryIcons = Object.freeze({
  [PostCategory.ANIME]: categoryAnimeIcon,
  [PostCategory.CREATION]: navCreateIcon,
  [PostCategory.GAME]: categoryGameIcon,
  [PostCategory.TECH]: categoryTechIcon,
  [PostCategory.DAILY]: categoryDailyIcon,
})
export default defineComponent({
  name: 'FeedFilters',
  props: { category: { type: String, default: undefined }, sort: { type: String, required: true } },
  emits: ['change'],
  setup(props, { emit }) {
    const categoryFilters = ref(null),
      sortFilters = ref(null)
    const deps = () => [props.category, props.sort]
    const categorySlider = useSlider(categoryFilters, { item: 'button', deps }),
      sortSlider = useSlider(sortFilters, { item: 'button', deps })
    // 鼠标停留到滑块到位后自动切换，快速划过不触发
    let hoverTimer
    function cancelHover() {
      clearTimeout(hoverTimer)
    }
    const isCurrent = (change) =>
      Object.entries(change).every(([key, value]) => props[key] === value)
    function hoverSelect(event, change) {
      cancelHover()
      // 触屏靠点击；停留期间若已经是当前项也不再重复切换
      if (event.pointerType === 'touch' || isCurrent(change)) return
      hoverTimer = setTimeout(() => {
        if (!isCurrent(change)) emit('change', change)
      }, HOVER_SELECT_MS)
    }
    onUnmounted(cancelHover)
    return {
      hoverSelect,
      cancelHover,
      categoryAllIcon,
      categoryIcons,
      ...useI18n(),
      Categories,
      PostSort,
      categoryFilters,
      sortFilters,
      categorySlider,
      sortSlider,
      sortValue: computed(() => props.sort),
    }
  },
})
