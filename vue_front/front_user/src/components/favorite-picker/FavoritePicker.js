import { computed, defineComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Close, FolderOpened } from '@element-plus/icons-vue'
import { useFavoriteStore } from '@/stores/favorites'
import { contentImage } from '@/api/content'
import { Account } from '@/constants/account'
import { accountFolderCover } from '@/assets/images'
import FeedPagination from '@/components/feed-pagination/FeedPagination.vue'
import PageState from '@/components/page-state/PageState.vue'
export default defineComponent({
  name: 'FavoritePicker',
  components: { Check, Close, FolderOpened, FeedPagination, PageState },
  setup() {
    const favorites = useFavoriteStore(),
      { t } = useI18n()
    function pick(folder) {
      if (!favorites.toggle(folder.id)) {
        ElMessage.warning(t('account.limitReached', { max: Account.POST_FOLDERS_MAX }))
      }
    }
    // 有没保存的勾选时离开要先确认，避免白选
    async function allowLeave() {
      if (!favorites.dirty) return true
      try {
        await ElMessageBox.confirm(t('account.discardChanges'), t('account.confirm'), {
          confirmButtonText: t('account.discard'),
          cancelButtonText: t('account.keepEditing'),
          type: 'warning',
        })
        return true
      } catch {
        return false
      }
    }
    async function leave() {
      if (await allowLeave()) favorites.close()
    }
    async function beforeClose(done) {
      if (await allowLeave()) done()
    }
    return {
      t,
      favorites,
      Account,
      pick,
      leave,
      beforeClose,
      contentImage,
      accountFolderCover,
      totalPages: computed(() => Math.ceil(favorites.total / Account.PICKER_SIZE)),
    }
  },
})
