import { computed, defineAsyncComponent, defineComponent, ref } from 'vue'
import { Picture as PictureIcon } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { contentImage } from '@/api/content'
export default defineComponent({
  name: 'PostImages',
  components: {
    PictureIcon,
    ImageViewer: defineAsyncComponent(() => import('@/components/image-viewer/ImageViewer.vue')),
  },
  props: { images: { type: Array, default: () => [] }, compact: Boolean },
  setup(props) {
    const previewIndex = ref(null)
    return {
      ...useI18n(),
      contentImage,
      previewIndex,
      viewerUrls: computed(() => props.images.map((image) => contentImage(image.url))),
      viewerThumbs: computed(() => props.images.map((image) => contentImage(image.thumbUrl))),
    }
  },
})
