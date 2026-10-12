import { computed, defineComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { MessageAvatarTones } from '@/constants/im'
export default defineComponent({
  name: 'MessageAvatar',
  props: { user: { type: Object, required: true }, size: { type: Number, default: 48 } },
  setup(props) {
    const { t } = useI18n()
    return {
      name: computed(() => props.user.nickname || t('home.authorFallback')),
      tone: computed(() => MessageAvatarTones[Number(props.user.id) % MessageAvatarTones.length]),
    }
  },
})
