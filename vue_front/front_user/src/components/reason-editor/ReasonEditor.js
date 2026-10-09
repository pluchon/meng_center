import { defineComponent, ref, watch, onMounted, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { Auth } from '@/constants/app'
export default defineComponent({
  name: 'ReasonEditor',
  props: { modelValue: { type: String, default: '' } },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    const editor = ref(null),
      composing = ref(false),
      id = useId()
    function update() {
      if (composing.value) return
      const value = editor.value.innerText.replace(/\r\n/g, '\n')
      const limited = value.slice(0, Auth.REASON_MAX)
      if (limited !== value) {
        editor.value.textContent = limited
        const selection = window.getSelection(),
          range = document.createRange()
        range.selectNodeContents(editor.value)
        range.collapse(false)
        selection.removeAllRanges()
        selection.addRange(range)
      }
      emit('update:modelValue', limited)
    }
    function beforeInput(event) {
      if (composing.value || event.isComposing || !event.data) return
      const selected = window.getSelection()?.toString().length || 0
      if (editor.value.innerText.length - selected + event.data.length > Auth.REASON_MAX)
        event.preventDefault()
    }
    function paste(event) {
      event.preventDefault()
      const selection = window.getSelection()
      if (!selection?.rangeCount) return
      const range = selection.getRangeAt(0)
      if (!editor.value.contains(range.commonAncestorContainer)) return
      const available =
        Auth.REASON_MAX - editor.value.innerText.length + selection.toString().length
      const text = event.clipboardData.getData('text/plain').slice(0, Math.max(0, available))
      range.deleteContents()
      const node = document.createTextNode(text)
      range.insertNode(node)
      range.setStartAfter(node)
      range.collapse(true)
      selection.removeAllRanges()
      selection.addRange(range)
      update()
    }
    function compositionEnd() {
      composing.value = false
      update()
    }
    function sync(value) {
      if (editor.value && editor.value.innerText !== value) editor.value.textContent = value
    }
    onMounted(() => sync(props.modelValue))
    watch(() => props.modelValue, sync)
    return {
      t: useI18n().t,
      editor,
      composing,
      update,
      beforeInput,
      paste,
      compositionEnd,
      Auth,
      labelId: id + '-label',
      counterId: id + '-count',
    }
  },
})
