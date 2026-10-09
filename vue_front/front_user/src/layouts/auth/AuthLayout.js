import BorderGlow from '@/components/border-glow/BorderGlow.vue'
import AuthDecor from '@/components/auth-decor/AuthDecor.vue'
import { AuthDecorVariant } from '@/constants/visual'
import { defineComponent, ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Picture as PosterIcon } from '@element-plus/icons-vue'
import loginPoster from '@/assets/login-poster.png'
import registerPoster from '@/assets/register-poster.png'
import brandIcon from '@/assets/meng_center_ico.png'
export default defineComponent({
  name: 'AuthLayout',
  components: { AuthDecor, BorderGlow, PosterIcon },
  props: { registration: Boolean },
  setup(props) {
    return {
      t: useI18n().t,
      brandIcon,
      imageFailed: ref(false),
      poster: computed(() => (props.registration ? registerPoster : loginPoster)),
      decor: computed(() =>
        props.registration ? AuthDecorVariant.REGISTER : AuthDecorVariant.LOGIN,
      ),
    }
  },
})
