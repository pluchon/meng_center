import { defineComponent, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { userApi } from '@/api/user'
import { useUserStore } from '@/stores/user'
import { useAsyncState } from '@/composables/useAsyncState'
import { UserStatus, Paging, Auth } from '@/constants/app'
import { RouteName } from '@/router/names'
import PageState from '@/components/page-state/PageState.vue'
export default defineComponent({
  name: 'ReviewView',
  components: { PageState },
  setup() {
    const { t } = useI18n(),
      user = useUserStore(),
      router = useRouter(),
      page = ref(Paging.FIRST),
      status = ref(''),
      busy = ref(false),
      failure = ref('')
    const state = useAsyncState(() =>
      userApi.registrations({
        page: page.value,
        size: Paging.SIZE,
        status: status.value || undefined,
      }),
    )
    onMounted(() => state.execute())
    function filter() {
      page.value = Paging.FIRST
      return state.execute()
    }
    async function approve(id) {
      if (busy.value) return
      try {
        await ElMessageBox.confirm(t('user.review.confirmApproval'), t('user.review.approve'), {
          confirmButtonText: t('common.confirm'),
          cancelButtonText: t('common.cancel'),
        })
      } catch {
        return
      }
      await update(() => userApi.approve(id))
    }
    async function reject(id) {
      if (busy.value) return
      let reason
      try {
        const result = await ElMessageBox.prompt(
          t('user.review.rejectReason'),
          t('user.review.rejectTitle'),
          {
            inputType: 'textarea',
            inputValidator: (value) =>
              (!!value?.trim() && value.length <= Auth.REASON_MAX) || t('user.review.rejectReason'),
            confirmButtonText: t('common.confirm'),
            cancelButtonText: t('common.cancel'),
          },
        )
        reason = result.value.trim()
      } catch {
        return
      }
      await update(() => userApi.reject(id, reason))
    }
    async function update(action) {
      busy.value = true
      failure.value = ''
      try {
        await action()
        ElMessage.success(t('user.review.updated'))
        await state.execute()
      } catch (error) {
        failure.value = error.message
      } finally {
        busy.value = false
      }
    }
    async function logout() {
      await user.logout()
      await router.replace({ name: RouteName.LOGIN })
    }
    return {
      t,
      user,
      page,
      status,
      busy,
      failure,
      filter,
      approve,
      reject,
      logout,
      ...state,
      load: state.execute,
      UserStatus,
      Paging,
    }
  },
})
