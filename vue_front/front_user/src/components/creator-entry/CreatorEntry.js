import { defineComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  IconClock,
  IconPencil,
  IconTrash,
  IconEye,
  IconHeart,
  IconMessage,
  IconStar,
  IconPhoto,
} from '@tabler/icons-vue'
import { contentImage } from '@/api/content'
import { RouteName } from '@/router/names'
import { usePostTime } from '@/composables/usePostTime'
import { communitySparkle } from '@/assets/images'
export default defineComponent({
  name: 'CreatorEntry',
  components: {
    IconClock,
    IconPencil,
    IconTrash,
    IconEye,
    IconHeart,
    IconMessage,
    IconStar,
    IconPhoto,
  },
  props: {
    entry: { type: Object, required: true },
    draft: { type: Boolean, default: false },
    pending: { type: Boolean, default: false },
  },
  emits: ['open', 'delete'],
  setup(props, { emit }) {
    function open(event) {
      if (
        props.draft ||
        event.defaultPrevented ||
        event.target.closest('a,button') ||
        window.getSelection()?.toString()
      )
        return
      emit('open', props.entry.id)
    }
    return {
      ...useI18n(),
      relativeTime: usePostTime(),
      contentImage,
      RouteName,
      communitySparkle,
      open,
    }
  },
})
