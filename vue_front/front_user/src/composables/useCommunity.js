import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
export function useCommunity() {
  const { t } = useI18n()
  return { t, unavailable: () => ElMessage.info(t('home.unavailable')) }
}
