import { defineComponent, computed, ref, nextTick } from 'vue'
import FieldMessage from '@/components/field-message/FieldMessage.vue'
import { useI18n } from 'vue-i18n'
import { ContactChannel, Regions } from '@/constants/app'
import ContactSwitchIcon from '@/components/contact-switch-icon/ContactSwitchIcon.vue'
import { authMailIcon } from '@/assets/images'
export default defineComponent({
  name: 'ContactField',
  components: { ContactSwitchIcon, FieldMessage },
  props: {
    error: { type: String, default: '' },
    messageId: { type: String, required: true },
    channel: { type: String, required: true },
    email: { type: String, default: '' },
    phone: { type: String, default: '' },
    countryCode: { type: String, required: true },
    disabled: Boolean,
  },
  emits: ['update:channel', 'update:email', 'update:phone', 'update:countryCode'],
  setup(props, { emit }) {
    const { t } = useI18n(),
      switching = ref(false)
    const model = (key) =>
      computed({ get: () => props[key], set: (value) => emit('update:' + key, value) })
    async function switchChannel() {
      switching.value = false
      await nextTick()
      switching.value = true
      emit(
        'update:channel',
        props.channel === ContactChannel.SMS ? ContactChannel.EMAIL : ContactChannel.SMS,
      )
    }
    return {
      t,
      mailIcon: authMailIcon,
      switching,
      switchChannel,
      ContactChannel,
      Regions,
      emailValue: model('email'),
      phoneValue: model('phone'),
      countryCodeValue: model('countryCode'),
    }
  },
})
