import { defineComponent, ref } from 'vue'
import { Picture as PictureIcon } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { contentImage } from '@/api/content'
export default defineComponent({
  name: 'PostImages',
  components: { PictureIcon },
  props: { images: { type: Array, default: () => [] }, compact: Boolean },
  setup() {
    const previewIndex = ref(null)
    return { ...useI18n(), contentImage, previewIndex }
  },
})
