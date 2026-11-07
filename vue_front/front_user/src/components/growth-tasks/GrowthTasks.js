import { computed, defineComponent, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  growthCalendarIcon,
  growthCheckIcon,
  editorPencilIcon,
  creatorCommentsIcon,
  creatorLikesIcon,
} from '@/assets/images'
import { ExpSource, SourceTones, GrowthCenter } from '@/constants/growth'
import { RouteName } from '@/router/names'
import FeedPagination from '@/components/feed-pagination/FeedPagination.vue'
// 今日任务：任务种类是后端配置的固定几项，按页显示，翻页器固定在卡片底部，只有一页也显示
export default defineComponent({
  name: 'GrowthTasks',
  components: { FeedPagination },
  props: { tasks: { type: Array, required: true } },
  setup(props) {
    const { t } = useI18n()
    const icons = {
      CHECK_IN: growthCalendarIcon,
      POST: editorPencilIcon,
      COMMENT: creatorCommentsIcon,
      LIKE: creatorLikesIcon,
    }
    const page = ref(1)
    const totalPages = computed(() =>
      Math.max(1, Math.ceil(props.tasks.length / GrowthCenter.TASK_SIZE)),
    )
    watch(totalPages, (value) => {
      if (page.value > value) page.value = value
    })
    return {
      t,
      icons,
      checkIcon: growthCheckIcon,
      ExpSource,
      SourceTones,
      RouteName,
      page,
      totalPages,
      visible: computed(() =>
        props.tasks.slice(
          (page.value - 1) * GrowthCenter.TASK_SIZE,
          page.value * GrowthCenter.TASK_SIZE,
        ),
      ),
    }
  },
})
