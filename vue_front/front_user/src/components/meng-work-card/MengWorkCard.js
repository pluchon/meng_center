import { defineAsyncComponent, defineComponent, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { IconFileText, IconPhoto } from '@tabler/icons-vue'
import { companionApi, companionFile } from '@/api/companion'
import { useRequestResource } from '@/composables/useRequestResource'
export default defineComponent({
  name: 'MengWorkCard',
  components: {
    IconFileText,
    IconPhoto,
    MengToy: defineAsyncComponent(() => import('@/components/meng-toy/MengToy.vue')),
  },
  props: {
    workId: { type: [Number, String], required: true },
    title: { type: String, default: '' },
    item: { type: Object, default: null },
  },
  emits: ['open'],
  setup(props) {
    const resource = useRequestResource(
      (signal) => companionApi.work(props.workId, { signal, silent: true }),
      props.item,
    )
    onMounted(() => {
      if (
        !resource.state.data ||
        (resource.state.data.kind !== 'IMAGE' && resource.state.data.body == null)
      )
        void resource.load()
    })
    return { ...useI18n(), resource, fileUrl: companionFile }
  },
})
