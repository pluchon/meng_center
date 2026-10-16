import { defineComponent, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { contentApi } from '@/api/content'
import { userApi, avatarUrl } from '@/api/user'
import { RouteName } from '@/router/names'
import { editorMagicIcon, userAvatar } from '@/assets/images'
import { notifyFailure } from '@/composables/useFailureNotice'
import GlowRing from '@/components/glow-ring/GlowRing.vue'

// 关注成功后让「已关注」停一会儿再收起，免得点完卡片立刻消失没有反馈
const LEAVE_DELAY = 900
// 每批几位
const PAGE_SIZE = 5

// 首页推荐流上面的「你可能想关注」：没有可推荐的人就整块不显示
export default defineComponent({
  name: 'RecommendedAuthors',
  components: { GlowRing },
  setup() {
    const authors = ref([]),
      loading = ref(true),
      refreshing = ref(false),
      batch = ref(1),
      total = ref(0),
      pending = reactive({}),
      followed = reactive({})
    const timers = new Set()
    let controller
    const fetchBatch = (page) =>
      contentApi.authors({ page, size: PAGE_SIZE }, { signal: controller.signal, silent: true })
    async function load() {
      controller = new AbortController()
      try {
        const result = await fetchBatch(1)
        authors.value = result.records
        total.value = result.total
      } catch {
        // 这一块只是锦上添花，取不到就不显示，不打断首页
        authors.value = []
      } finally {
        loading.value = false
      }
    }
    // 换一批：后端按顺序一批批给，看完最后一批再点回到第一批并重新算；换的过程中不能重复点
    async function refresh() {
      if (refreshing.value) return
      refreshing.value = true
      try {
        let next = batch.value * PAGE_SIZE < total.value ? batch.value + 1 : 1
        let result = await fetchBatch(next)
        // 后面没有了（比如剩下的都已关注），回到第一批
        if (!result.records.length && next > 1) {
          next = 1
          result = await fetchBatch(1)
        }
        if (result.records.length) {
          authors.value = result.records
          total.value = result.total
          batch.value = next
          Object.keys(followed).forEach((id) => delete followed[id])
        }
      } catch (error) {
        if (!error.cancelled) notifyFailure(error)
      } finally {
        refreshing.value = false
      }
    }
    async function follow(item) {
      const id = item.author.id
      if (pending[id] || followed[id]) return
      pending[id] = true
      try {
        await userApi.follow(id, true)
        followed[id] = true
        const timer = setTimeout(() => {
          timers.delete(timer)
          authors.value = authors.value.filter((row) => row.author.id !== id)
        }, LEAVE_DELAY)
        timers.add(timer)
      } catch (error) {
        notifyFailure(error)
      } finally {
        delete pending[id]
      }
    }
    onMounted(load)
    onUnmounted(() => {
      controller?.abort()
      timers.forEach(clearTimeout)
    })
    return {
      ...useI18n(),
      RouteName,
      editorMagicIcon,
      userAvatar,
      avatarUrl,
      authors,
      loading,
      refreshing,
      batch,
      total,
      size: PAGE_SIZE,
      refresh,
      pending,
      followed,
      follow,
    }
  },
})
