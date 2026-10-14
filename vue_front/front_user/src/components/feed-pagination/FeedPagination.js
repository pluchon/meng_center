import { defineComponent, computed, ref } from 'vue'
import { feedPages } from '@/composables/feedPages'
import { useSlider } from '@/composables/useSlider'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, ArrowRight, Top } from '@element-plus/icons-vue'
import { communitySparkle } from '@/assets/images'
export default defineComponent({
  name: 'FeedPagination',
  components: { ArrowLeft, ArrowRight, Top },
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
    const nav = ref(null)
    // 选中页的金色贴纸跟着页码滑动；翻页器不跟随悬停，所以不绑悬停事件
    const slider = useSlider(nav, {
      item: 'button:not(.step)',
      deps: () => [props.page, props.totalPages, props.compact],
    })
    return {
      t,
      pages,
      nav,
      slider,
      communitySparkle,
    }
  },
})
