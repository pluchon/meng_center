import { computed, defineAsyncComponent, defineComponent, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { imImage } from '@/api/im'
import { Im } from '@/constants/im'
export default defineComponent({
  name: 'MessageImages',
  components: {
    ImageViewer: defineAsyncComponent(() => import('@/components/image-viewer/ImageViewer.vue')),
  },
  props: { images: { type: Array, default: () => [] } },
  setup(props) {
    const expanded = ref(false),
      preview = ref(null)
    return {
      ...useI18n(),
      expanded,
      preview,
      imImage,
      album: computed(() => props.images.length >= Im.ALBUM_MIN),
      urls: computed(() => props.images.map((item) => imImage(item.id))),
    }
  },
})
