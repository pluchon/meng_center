import { defineComponent } from 'vue'
import { useI18n } from 'vue-i18n'
// 统一的对话框外壳：圆形图标贴纸、歪着的标题、蓝色关闭贴纸、虚线分区，样式在 styles/dialog.scss
export default defineComponent({
  name: 'DialogShell',
  props: {
    modelValue: { type: Boolean, default: false },
    title: { type: String, default: '' },
    // 右上角探出来的颜文字小气泡，可不要
    face: { type: String, default: '' },
    icon: { type: [Object, Function], required: true },
    // 语义色：gold（默认）、pink、blue、mint、violet
    tone: { type: String, default: 'gold' },
    width: { type: String, default: 'min(520px,calc(100vw - 32px))' },
    busy: { type: Boolean, default: false },
  },
  emits: ['close'],
  setup() {
    return useI18n()
  },
})
