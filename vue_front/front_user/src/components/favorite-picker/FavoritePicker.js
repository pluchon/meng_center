import { defineComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { FolderOpened } from '@element-plus/icons-vue'
import { useFavoriteStore } from '@/stores/favorites'
export default defineComponent({
  name: 'FavoritePicker',
  components: { FolderOpened },
  setup() {
    return { ...useI18n(), favorites: useFavoriteStore() }
  },
})
