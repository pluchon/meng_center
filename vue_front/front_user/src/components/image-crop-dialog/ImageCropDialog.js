import { computed, defineComponent, onUnmounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { IconCrop, IconPhoto, IconZoomIn, IconZoomOut } from '@tabler/icons-vue'
import DialogShell from '@/components/dialog-shell/DialogShell.vue'
import FeedPagination from '@/components/feed-pagination/FeedPagination.vue'

const TYPES = ['image/jpeg', 'image/png', 'image/webp']
// 右侧预设图栏的宽度，和裁剪区之间隔 20px；弹窗左右内边距加描边一共约 65px
const PANEL_WIDTH = 400
const PANEL_GAP = 20
const DIALOG_EDGE = 65

// 裁剪图片的弹窗：拖动挪位置，滑块或滚轮缩放，可以换一张，也可以从右侧展开的预设图里挑；
// 展开时弹窗只是变宽，裁剪区和高度都不动，预设图栏从右边露出来；
// 只有点确定才把裁好的图交给调用方，什么时候上传由调用方决定
export default defineComponent({
  name: 'ImageCropDialog',
  components: { DialogShell, FeedPagination, IconPhoto, IconZoomIn, IconZoomOut },
  props: {
    file: { type: File, required: true },
    // 预设图：{ key, label, src }，src 为空的是还没画好的占位
    presets: { type: Array, default: () => [] },
    // 裁剪框的宽高比
    aspect: { type: Number, required: true },
    // 输出图片的宽度，高度按比例算
    outputWidth: { type: Number, default: 960 },
    // 裁剪框的宽度，高度按宽高比算
    frameWidth: { type: Number, default: 456 },
    // 预设图栏每页几列几行
    presetColumns: { type: Number, default: 3 },
    presetRows: { type: Number, default: 2 },
    // 头像：裁剪框上叠一个圆形取景，预设卡片也画成圆的
    round: { type: Boolean, default: false },
    fileName: { type: String, default: 'cover.webp' },
    maxBytes: { type: Number, default: 5 * 1024 * 1024 },
    title: { type: String, default: '' },
    invalidText: { type: String, default: '' },
  },
  emits: ['confirm', 'close'],
  setup(props, { emit }) {
    const { t } = useI18n()
    const frame = ref(null),
      url = ref(''),
      ready = ref(false),
      zoom = ref(1)
    const natural = reactive({ width: 0, height: 0 }),
      offset = reactive({ x: 0, y: 0 }),
      box = reactive({ width: 0, height: 0 })
    let pointer = null
    const presetsOpen = ref(false),
      presetPage = ref(1),
      chosen = ref('')
    const pageSize = computed(() => props.presetColumns * props.presetRows)
    const presetPages = computed(() =>
      Math.max(1, Math.ceil(props.presets.length / pageSize.value)),
    )
    const shownPresets = computed(() =>
      props.presets.slice((presetPage.value - 1) * pageSize.value, presetPage.value * pageSize.value),
    )
    const dialogWidth = computed(() => {
      const width = props.frameWidth + DIALOG_EDGE + (presetsOpen.value ? PANEL_GAP + PANEL_WIDTH : 0)
      return 'min(' + width + 'px,calc(100vw - 32px))'
    })
    // 最小缩放让图片刚好盖满裁剪框，滑块在它的 1～3 倍之间
    const cover = computed(() =>
      natural.width ? Math.max(box.width / natural.width, box.height / natural.height) : 1,
    )
    const scale = computed(() => cover.value * zoom.value)
    // 传文件就生成临时地址，传字符串就是预设图的地址
    function open(source) {
      if (url.value.startsWith('blob:')) URL.revokeObjectURL(url.value)
      ready.value = false
      url.value = typeof source === 'string' ? source : URL.createObjectURL(source)
    }
    function pick(preset) {
      chosen.value = preset.key
      if (preset.src) open(preset.src)
      else ElMessage.info(t('common.crop.presetPending'))
    }
    function clamp() {
      const width = natural.width * scale.value,
        height = natural.height * scale.value
      offset.x = Math.min(0, Math.max(box.width - width, offset.x))
      offset.y = Math.min(0, Math.max(box.height - height, offset.y))
    }
    function loaded(event) {
      natural.width = event.target.naturalWidth
      natural.height = event.target.naturalHeight
      box.width = frame.value.clientWidth
      box.height = frame.value.clientHeight
      chosen.value = props.presets.find((preset) => preset.src === url.value)?.key || ''
      zoom.value = 1
      offset.x = (box.width - natural.width * cover.value) / 2
      offset.y = (box.height - natural.height * cover.value) / 2
      applied = scale.value
      ready.value = true
    }
    // 缩放时以裁剪框中心为准，画面不会突然跳走。applied 是取景已经对齐过的缩放，
    // 换图和量尺寸时直接改它，不走这里，免得拿换图前的旧缩放去挪画面
    let applied = 0
    watch(scale, (next) => {
      if (!ready.value || !applied || next === applied) return
      const ratio = next / applied
      applied = next
      offset.x = box.width / 2 - (box.width / 2 - offset.x) * ratio
      offset.y = box.height / 2 - (box.height / 2 - offset.y) * ratio
      clamp()
    })
    function grab(event) {
      if (!ready.value) return
      pointer = { id: event.pointerId, x: event.clientX - offset.x, y: event.clientY - offset.y }
      frame.value.setPointerCapture(event.pointerId)
    }
    function drag(event) {
      if (pointer?.id !== event.pointerId) return
      offset.x = event.clientX - pointer.x
      offset.y = event.clientY - pointer.y
      clamp()
    }
    function release() {
      pointer = null
    }
    function wheel(event) {
      zoom.value = Math.min(3, Math.max(1, zoom.value - event.deltaY * 0.001))
    }
    function replace(event) {
      const file = event.target.files[0]
      event.target.value = ''
      if (!file) return
      if (!TYPES.includes(file.type) || file.size > props.maxBytes) {
        ElMessage.warning(props.invalidText)
        return
      }
      open(file)
    }
    function confirm() {
      const image = frame.value.querySelector('img')
      const canvas = document.createElement('canvas')
      canvas.width = props.outputWidth
      canvas.height = Math.round(props.outputWidth / props.aspect)
      canvas
        .getContext('2d')
        .drawImage(
          image,
          -offset.x / scale.value,
          -offset.y / scale.value,
          box.width / scale.value,
          box.height / scale.value,
          0,
          0,
          canvas.width,
          canvas.height,
        )
      canvas.toBlob(
        (blob) => {
          if (blob) emit('confirm', new File([blob], props.fileName, { type: 'image/webp' }))
        },
        'image/webp',
        0.9,
      )
    }
    // 窗口太窄时裁剪框会跟着缩：重新量一次，按比例保住当前取景
    const observer = new ResizeObserver(() => {
      if (!ready.value || !frame.value) return
      const ratio = frame.value.clientWidth / box.width
      if (!ratio || ratio === 1) return
      // 框和图一起按同一个比例缩放
      box.width = frame.value.clientWidth
      box.height = frame.value.clientHeight
      applied = scale.value
      offset.x *= ratio
      offset.y *= ratio
      clamp()
    })
    open(props.file)
    // 弹窗内容比组件晚渲染，等裁剪框真的出现再开始观察
    watch(frame, (element, previous) => {
      if (previous) observer.unobserve(previous)
      if (element) observer.observe(element)
    })
    onUnmounted(() => {
      observer.disconnect()
      if (url.value.startsWith('blob:')) URL.revokeObjectURL(url.value)
    })
    return {
      t,
      IconCrop,
      frame,
      url,
      ready,
      zoom,
      natural,
      offset,
      scale,
      loaded,
      grab,
      drag,
      release,
      wheel,
      replace,
      confirm,
      presetsOpen,
      dialogWidth,
      panelWidth: PANEL_WIDTH,
      presetPage,
      presetPages,
      shownPresets,
      chosen,
      pick,
    }
  },
})
