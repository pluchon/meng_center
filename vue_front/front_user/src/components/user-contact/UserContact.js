import { defineComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { RouteName } from '@/router/names'
import { userAvatar } from '@/assets/images'
import { avatarUrl } from '@/api/user'
export default defineComponent({
  name: 'UserContact',
  props: { author: { type: Object, required: true }, size: { type: Number, default: 40 } },
  setup(props) {
    const { t } = useI18n(),
      user = useUserStore(),
      router = useRouter()
    function message() {
      const target = { name: RouteName.MESSAGES, query: { with: String(props.author.id) } }
      void router.push(
        user.loggedIn
          ? target
          : { name: RouteName.LOGIN, query: { redirect: router.resolve(target).fullPath } },
      )
    }
    return { t, user, message, userAvatar, avatarUrl }
  },
})
