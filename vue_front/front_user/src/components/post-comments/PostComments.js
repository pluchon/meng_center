import { computed, defineComponent, nextTick, reactive, ref, watch, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { EditPen } from '@element-plus/icons-vue'
import { Comment, ReportTarget } from '@/constants/comments'
import { contentApi } from '@/api/content'
import { notifyFailure } from '@/composables/useFailureNotice'
import FeedPagination from '@/components/feed-pagination/FeedPagination.vue'
import PageState from '@/components/page-state/PageState.vue'
import CommentItem from '@/components/comment-item/CommentItem.vue'
import CommentComposer from '@/components/comment-composer/CommentComposer.vue'
import CollapsePanel from '@/components/collapse-panel/CollapsePanel.vue'
export default defineComponent({
  name: 'PostComments',
  components: { CommentItem, CommentComposer, CollapsePanel, PageState, EditPen, FeedPagination },
  props: {
    postId: { type: [String, Number], required: true },
    count: { type: Number, default: 0 },
    initialPage: { type: Object, default: null },
  },
  emits: ['count', 'report'],
  setup(props, { emit }) {
    const { t } = useI18n(),
      records = ref([]),
      total = ref(0),
      page = ref(1),
      loading = ref(false),
      error = ref(null),
      open = ref(false),
      composerMounted = ref(false),
      target = ref(null),
      composer = ref(null),
      section = ref(null),
      replies = reactive({}),
      pending = reactive({})
    let generation = 0
    let initialPage = props.initialPage
    const totalPages = computed(() => Math.ceil(total.value / Comment.SIZE))
    async function load() {
      const current = ++generation
      loading.value = true
      error.value = null
      try {
        const prefetched = initialPage
        initialPage = null
        const initial = prefetched ? await prefetched : null
        if (initial?.error) throw initial.error
        const result =
          initial?.result ??
          (await contentApi.comments(props.postId, {
            page: page.value,
            size: Comment.SIZE,
          }))
        if (current !== generation) return
        records.value = result.records
        total.value = result.total
        if (page.value > 1 && !result.records.length) {
          page.value = Math.max(1, page.value - 1)
          return
        }
      } catch (e) {
        if (current === generation) error.value = e
      } finally {
        if (current === generation) loading.value = false
      }
    }
    watch(
      () => props.postId,
      () => {
        generation++
        page.value = 1
        open.value = false
        target.value = null
        records.value = []
        Object.keys(replies).forEach((key) => delete replies[key])
        void load()
      },
      { immediate: true },
    )
    watch(page, () => {
      void load()
    })
    onUnmounted(() => generation++)
    function state(id) {
      if (!replies[id])
        replies[id] = { open: false, loading: false, error: null, records: [], total: 0, page: 0 }
      return replies[id]
    }
    async function loadReplies(comment, reset = false) {
      const s = state(comment.id)
      if (s.loading) return
      s.loading = true
      s.error = null
      const current = generation
      try {
        const result = await contentApi.replies(comment.id, {
          page: reset ? 1 : s.page + 1,
          size: Comment.SIZE,
        })
        if (current !== generation) return
        s.records = reset
          ? result.records
          : [
              ...s.records,
              ...result.records.filter((c) => !s.records.some((old) => old.id === c.id)),
            ]
        s.records.sort((a, b) => a.id - b.id)
        s.total = result.total
        s.page = result.page
      } catch (e) {
        if (current === generation) s.error = e
      } finally {
        s.loading = false
      }
    }
    function expand(comment) {
      const s = state(comment.id)
      s.open = !s.open
      if (s.open && !s.page) void loadReplies(comment, true)
    }
    async function reply(comment) {
      target.value = comment
      composerMounted.value = true
      open.value = true
      await nextTick()
      composer.value?.focus()
      section.value?.scrollIntoView({
        behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
        block: 'start',
      })
    }
    async function toggleComposer() {
      open.value = !open.value
      if (open.value) {
        composerMounted.value = true
        await nextTick()
        composer.value?.focus()
      }
    }
    async function like(comment) {
      if (pending[comment.id]) return
      pending[comment.id] = true
      const old = comment.liked
      comment.liked = !old
      comment.likeCount += old ? -1 : 1
      try {
        await contentApi.commentLike(comment.id, !old)
      } catch (e) {
        comment.liked = old
        comment.likeCount += old ? 1 : -1
        notifyFailure(e)
      } finally {
        pending[comment.id] = false
      }
    }
    async function remove(comment) {
      if (pending[comment.id]) return
      try {
        await ElMessageBox.confirm(t('detail.deleteComment'), t('detail.delete'), {
          confirmButtonText: t('detail.confirm'),
          cancelButtonText: t('detail.cancel'),
          type: 'warning',
        })
      } catch {
        return
      }
      pending[comment.id] = true
      try {
        const count = await contentApi.deleteComment(comment.id)
        emit('count', count)
        const root = records.value.find((c) => c.id === comment.id)
        if (root) {
          if (
            target.value &&
            (target.value.id === root.id ||
              replies[root.id]?.records.some((c) => c.id === target.value.id))
          )
            target.value = null
          delete replies[root.id]
          await load()
        } else {
          for (const c of records.value) {
            const s = replies[c.id]
            if (s?.records.some((item) => item.id === comment.id)) {
              s.records = s.records.filter((item) => item.id !== comment.id)
              s.total--
              c.replyCount--
              break
            }
          }
        }
        if (
          target.value?.id === comment.id ||
          (root && target.value && replies[root.id]?.records.some((c) => c.id === target.value.id))
        )
          target.value = null
        ElMessage.success(t('detail.removed'))
      } catch (e) {
        notifyFailure(e)
      } finally {
        pending[comment.id] = false
      }
    }
    async function sent({ comment, target: replyTarget }) {
      emit('count', props.count + 1)
      open.value = false
      target.value = null
      ElMessage.success(t('detail.sent'))
      if (!replyTarget) {
        if (page.value !== 1) page.value = 1
        else await load()
      } else {
        const root = records.value.find(
          (c) =>
            c.id === replyTarget.id || replies[c.id]?.records.some((r) => r.id === replyTarget.id),
        )
        if (root) {
          root.replyCount++
          const s = state(root.id)
          s.open = true
          await loadReplies(root, true)
          if (!s.records.some((c) => c.id === comment.id)) s.records.push(comment)
          s.total = Math.max(s.total, root.replyCount)
        }
      }
    }
    function report(comment) {
      emit('report', { targetType: ReportTarget.COMMENT, targetId: comment.id })
    }
    return {
      t,
      records,
      total,
      page,
      totalPages,
      loading,
      error,
      open,
      composerMounted,
      target,
      composer,
      section,
      replies,
      pending,
      load,
      loadReplies,
      expand,
      reply,
      toggleComposer,
      like,
      remove,
      sent,
      report,
    }
  },
})
