import { defineComponent, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { IconFileText, IconPhoto } from '@tabler/icons-vue'
import { companionApi, companionFile } from '@/api/companion'
import { createCompanionDraft } from '@/api/companionWork'
import { useRequestResource } from '@/composables/useRequestResource'
import { RouteName } from '@/router/names'
import { notifyFailure } from '@/composables/useFailureNotice'
export default defineComponent({
  name: 'MengWorkCard',
  components: { IconFileText, IconPhoto },
  props: {
    workId: { type: [Number, String], required: true },
    title: { type: String, default: '' },
    item: { type: Object, default: null },
  },
  emits: ['open', 'revise'],
  setup(props, { emit }) {
    const { t } = useI18n(),
      router = useRouter(),
      busy = ref(false)
    let active = true
    const resource = useRequestResource(
      (signal) => companionApi.work(props.workId, { signal, silent: true }),
      props.item,
    )
    onMounted(() => {
      if (
        !resource.state.data ||
        (resource.state.data.kind === 'DOCUMENT' && resource.state.data.body == null)
      )
        void resource.load()
    })
    onBeforeUnmount(() => {
      active = false
    })
    async function toDraft() {
      if (busy.value || !resource.state.data) return
      busy.value = true
      try {
        const id = await createCompanionDraft(resource.state.data)
        if (active) await router.push({ name: RouteName.POST_NEW, query: { draft: String(id) } })
      } catch (error) {
        if (active) notifyFailure(error)
      } finally {
        busy.value = false
      }
    }
    return {
      t,
      resource,
      busy,
      toDraft,
      fileUrl: companionFile,
      revise: () => emit('revise', resource.state.data),
    }
  },
})
