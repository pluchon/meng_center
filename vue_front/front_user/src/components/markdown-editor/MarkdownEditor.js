import { defineComponent, ref, watch, onMounted, onUnmounted } from 'vue'
import { EditorView, minimalSetup } from 'codemirror'
import { EditorState, Compartment } from '@codemirror/state'
import { markdown } from '@codemirror/lang-markdown'
import { HighlightStyle, syntaxHighlighting } from '@codemirror/language'
import { tags } from '@lezer/highlight'
import { useI18n } from 'vue-i18n'
import { MarkdownFormats } from '@/constants/content'
export default defineComponent({
  name: 'MarkdownEditor',
  props: { modelValue: { type: String, default: '' }, disabled: Boolean },
  emits: ['update:modelValue'],
  setup(props, { emit, expose }) {
    const host = ref(null),
      { t } = useI18n(),
      editable = new Compartment()
    let view
    onMounted(() => {
      view = new EditorView({
        parent: host.value,
        state: EditorState.create({
          doc: props.modelValue,
          extensions: [
            minimalSetup,
            markdown(),
            EditorView.lineWrapping,
            editable.of(EditorView.editable.of(!props.disabled)),
            EditorView.contentAttributes.of({ 'aria-label': t('editor.body') }),
            syntaxHighlighting(
              HighlightStyle.define([
                { tag: tags.heading, color: 'var(--meng-primary)', fontWeight: '700' },
                { tag: tags.emphasis, fontStyle: 'italic' },
                { tag: tags.strong, fontWeight: '700' },
                { tag: tags.link, color: 'var(--meng-primary)' },
                { tag: tags.monospace, color: 'var(--meng-growth-green)' },
              ]),
            ),
            EditorView.updateListener.of((update) => {
              if (update.docChanged) emit('update:modelValue', update.state.doc.toString())
            }),
          ],
        }),
      })
    })
    watch(
      () => props.modelValue,
      (value) => {
        if (view && view.state.doc.toString() !== value)
          view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: value } })
      },
    )
    watch(
      () => props.disabled,
      (value) => view?.dispatch({ effects: editable.reconfigure(EditorView.editable.of(!value)) }),
    )
    onUnmounted(() => view?.destroy())
    expose({
      format(key) {
        if (!view || props.disabled) return
        const format = MarkdownFormats.find((item) => item.key === key)
        if (!format) return
        const { from, to } = view.state.selection.main,
          selected = view.state.sliceDoc(from, to) || t('editor.formatText')
        const text = format.line
          ? selected
              .split('\n')
              .map((line) => format.before + line)
              .join('\n')
          : format.before + selected + format.after
        view.dispatch({
          changes: { from, to, insert: text },
          selection: {
            anchor: from + format.before.length,
            head: from + text.length - (format.after?.length || 0),
          },
        })
        view.focus()
      },
    })
    return { host }
  },
})
