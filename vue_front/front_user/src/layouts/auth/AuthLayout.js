import BorderGlow from '@/components/border-glow/BorderGlow.vue'
import CardEdge from '@/components/card-edge/CardEdge.vue'
import AuthDecor from '@/components/auth-decor/AuthDecor.vue'
import { AuthDecorVariant } from '@/constants/visual'
import { defineComponent, ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Picture as PosterIcon } from '@element-plus/icons-vue'
import { brandIcon, loginPoster, registerPoster } from '@/assets/images'
export default defineComponent({
  name: 'AuthLayout',
  components: { AuthDecor, BorderGlow, CardEdge, PosterIcon },
  props: { registration: Boolean },
  setup(props) {
    return {
      t: useI18n().t,
      brandIcon,
      imageFailed: ref(false),
      posterLoaded: ref(false),
      poster: computed(() => (props.registration ? registerPoster : loginPoster)),
      decor: computed(() =>
        props.registration ? AuthDecorVariant.REGISTER : AuthDecorVariant.LOGIN,
      ),
    }
  },
})
