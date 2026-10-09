import { defineComponent, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/user'
import { useUserStore } from '@/stores/user'
import { useAsyncState } from '@/composables/useAsyncState'
import { RouteName } from '@/router/names'
import PageState from '@/components/page-state/PageState.vue'
export default defineComponent({
  name: 'AccountView',
  components: { PageState },
  setup() {
    const { t } = useI18n(),
      router = useRouter(),
      user = useUserStore(),
      state = useAsyncState(() => userApi.me())
    const password = reactive({ oldPassword: '', password: '', confirmPassword: '' }),
      failure = ref(''),
      submitting = ref(false)
    onMounted(() => state.execute())
    async function logout() {
      await user.logout()
      await router.replace({ name: RouteName.LOGIN })
    }
    async function changePassword() {
      if (submitting.value) return
      submitting.value = true
      failure.value = ''
      try {
        await userApi.password(password)
        user.clear()
        ElMessage.success(t('user.account.changed'))
        await router.replace({ name: RouteName.LOGIN })
      } catch (error) {
        failure.value = error.message
      } finally {
        submitting.value = false
      }
    }
    return {
      t,
      user,
      password,
      failure,
      submitting,
      logout,
      changePassword,
      ...state,
      load: state.execute,
    }
  },
})
