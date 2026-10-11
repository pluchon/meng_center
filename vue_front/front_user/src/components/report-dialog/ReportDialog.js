import { defineComponent, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { contentApi } from '@/api/content'
import { ReportReasons } from '@/constants/comments'
import { notifyFailure } from '@/composables/useFailureNotice'
export default defineComponent({
  name: 'ReportDialog',
  props: { target: { type: Object, default: null } },
  emits: ['close'],
  setup(props, { emit }) {
    const { t } = useI18n(),
      reason = ref(''),
      note = ref(''),
      busy = ref(false)
    watch(
      () => props.target,
      () => {
        reason.value = ''
        note.value = ''
      },
    )
    async function submit() {
      if (!props.target || !reason.value || busy.value) return
      const target = props.target
      busy.value = true
      try {
        await contentApi.report({ ...target, reason: reason.value, note: note.value })
        ElMessage.success(t('detail.reportReceived'))
        emit('close')
      } catch (error) {
        notifyFailure(error)
        if (error.code === 'CONTENT_REPORT_DUPLICATE') emit('close')
      } finally {
        busy.value = false
      }
    }
    return { t, reason, note, busy, ReportReasons, submit }
  },
})
