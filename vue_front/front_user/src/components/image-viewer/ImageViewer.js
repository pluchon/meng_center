import { computed, defineComponent, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ArrowLeft,
  ArrowRight,
  Download,
  FullScreen,
  Picture as PictureIcon,
  RefreshLeft,
  RefreshRight,
  ZoomIn,
  ZoomOut,
} from '@element-plus/icons-vue'
import { messageCloseIcon, messagePhotoIcon } from '@/assets/images'
// 缩放档位（百分比），默认停在 100%
const ZOOMS = Object.freeze([50, 75, 100, 150, 200, 300])
const DEFAULT_ZOOM = 2
// 缩略图条一次显示几张，每张占的宽度（含间距）见样式
const VISIBLE = 5
export default defineComponent({
  name: 'ImageViewer',
  components: {
    ArrowLeft,
    ArrowRight,
    Download,
    FullScreen,
    PictureIcon,
    RefreshLeft,
    RefreshRight,
    ZoomIn,
    ZoomOut,
  },
  props: {
    urls: { type: Array, required: true },
    thumbs: { type: Array, default: () => [] },
    // 和 urls 一一对应，为真的那张不能下载（隐藏下载按钮，也拦截右键另存和拖拽）
    noDownload: { type: Array, default: () => [] },
    initialIndex: { type: Number, default: 0 },
  },
  emits: ['close'],
  setup(props) {
    const open = ref(true),
      total = props.urls.length,
      maxStrip = Math.max(0, total - VISIBLE),
      index = ref(Math.min(Math.max(props.initialIndex, 0), total - 1)),
      zoom = ref(DEFAULT_ZOOM),
      rotation = ref(0),
      failed = ref(false),
      ready = ref(false),
      direction = ref('next'),
      size = ref(null),
      strip = ref(0),
      { t } = useI18n()
    // 让缩略图条停在能看见当前这张的位置
    function followStrip() {
      if (index.value < strip.value) strip.value = index.value
      else if (index.value > strip.value + VISIBLE - 1) strip.value = index.value - VISIBLE + 1
    }
    followStrip()
    function go(next) {
      if (next < 0 || next >= total) return
      direction.value = next > index.value ? 'next' : 'prev'
      index.value = next
      zoom.value = DEFAULT_ZOOM
      rotation.value = 0
      failed.value = false
      ready.value = false
      size.value = null
      followStrip()
    }
    function shift(step) {
      strip.value = Math.min(maxStrip, Math.max(0, strip.value + step))
    }
    function reset() {
      zoom.value = DEFAULT_ZOOM
      rotation.value = 0
    }
    function loaded(event) {
      ready.value = true
      size.value = { width: event.target.naturalWidth, height: event.target.naturalHeight }
    }
    function wheel(event) {
      zoom.value = Math.min(ZOOMS.length - 1, Math.max(0, zoom.value + (event.deltaY < 0 ? 1 : -1)))
    }
    const canDownload = computed(() => !props.noDownload[index.value])
    function blockSave(event) {
      if (!canDownload.value) event.preventDefault()
    }
    function download() {
      if (!canDownload.value) return
      const link = document.createElement('a')
      link.href = props.urls[index.value]
      link.download = decodeURIComponent(
        props.urls[index.value].split('?')[0].split('/').pop() || '',
      )
      link.rel = 'noopener'
      document.body.appendChild(link)
      link.click()
      link.remove()
    }
    function onKey(event) {
      if (event.key === 'ArrowLeft') go(index.value - 1)
      else if (event.key === 'ArrowRight') go(index.value + 1)
    }
    onMounted(() => document.addEventListener('keydown', onKey))
    onUnmounted(() => document.removeEventListener('keydown', onKey))
    return {
      t,
      open,
      total,
      maxStrip,
      index,
      zoom,
      rotation,
      failed,
      ready,
      direction,
      size,
      strip,
      go,
      shift,
      reset,
      loaded,
      wheel,
      download,
      canDownload,
      blockSave,
      ZOOMS,
      VISIBLE,
      messageCloseIcon,
      messagePhotoIcon,
      thumbList: computed(() => (props.thumbs.length === total ? props.thumbs : props.urls)),
    }
  },
})
