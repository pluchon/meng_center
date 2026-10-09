import { defineComponent, ref, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
// 大写锁定提醒：放进密码输入框所在的 label 里即可。
// 它自己监听这个 label 里的按键，页面不用再接事件；提示浮在标签文字那一行的右侧，不占版面
export default defineComponent({
  name: 'CapsLockHint',
  setup() {
    const anchor = ref(null),
      active = ref(false)
    let field
    function inspect(event) {
      if (event.target?.tagName !== 'INPUT' || !event.getModifierState) return
      active.value = event.getModifierState('CapsLock')
    }
    function hide() {
      active.value = false
    }
    onMounted(() => {
      field = anchor.value.parentElement
      field.addEventListener('keydown', inspect)
      field.addEventListener('keyup', inspect)
      field.addEventListener('focusout', hide)
    })
    onBeforeUnmount(() => {
      field?.removeEventListener('keydown', inspect)
      field?.removeEventListener('keyup', inspect)
      field?.removeEventListener('focusout', hide)
    })
    return { t: useI18n().t, anchor, active }
  },
})
