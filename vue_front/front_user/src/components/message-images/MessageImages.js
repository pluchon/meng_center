import { computed, defineAsyncComponent, defineComponent, ref } from 'vue'
import { useI18n } from 'vue-i18n'
export default defineComponent({
  name: 'MessageImages',
  components: {
    ImageViewer: defineAsyncComponent(() => import('@/components/image-viewer/ImageViewer.vue')),
  },
  // items 里每一项是 { kind: 'image' | 'emotion', url, thumb, name }；表情和图片一起排
  props: {
    items: { type: Array, default: () => [] },
    // 放在气泡里只有一张时用大一点的尺寸
    large: Boolean,
  },
  setup(props) {
    const preview = ref(null)
    return {
      ...useI18n(),
      preview,
      // 超过一张就叠成图集，点一下用图片查看器看全部，和帖子里的配图一样
      album: computed(() => props.items.length > 1),
      urls: computed(() => props.items.map((item) => item.url)),
      thumbs: computed(() => props.items.map((item) => item.thumb)),
      // 表情不提供下载
      noDownload: computed(() => props.items.map((item) => item.kind === 'emotion')),
      guard(item, event) {
        if (item.kind === 'emotion') event.preventDefault()
      },
    }
  },
})
