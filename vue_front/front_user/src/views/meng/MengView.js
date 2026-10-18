import { defineAsyncComponent, defineComponent, provide, toRefs, onMounted, onUnmounted } from 'vue'
import CommunityLayout from '@/layouts/community/CommunityLayout.vue'
import MengHome from '@/components/meng-home/MengHome.vue'
import MengState from '@/components/meng-state/MengState.vue'
import ShapeGrid from '@/components/shape-grid/ShapeGrid.vue'
import { useCompanion } from '@/composables/useCompanion'
import { CompanionContext } from '@/constants/meng'
export default defineComponent({
  name: 'MengView',
  components: {
    CommunityLayout,
    MengHome,
    MengState,
    ShapeGrid,
    MengChat: defineAsyncComponent(() => import('@/components/meng-chat/MengChat.vue')),
    MengDialogs: defineAsyncComponent(() => import('@/components/meng-dialogs/MengDialogs.vue')),
  },
  setup() {
    const context = useCompanion()
    provide(CompanionContext, context)
    onMounted(() => document.documentElement.classList.add('meng-scrollbar-hidden'))
    onUnmounted(() => document.documentElement.classList.remove('meng-scrollbar-hidden'))
    return { ...toRefs(context) }
  },
})
