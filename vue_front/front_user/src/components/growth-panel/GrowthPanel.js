import { defineComponent } from 'vue'
import { detailBubble, detailFlower, communitySparkle } from '@/assets/images'
export default defineComponent({
  name: 'GrowthPanel',
  props: {
    title: { type: String, default: '' },
    icon: { type: String, default: '' },
    tone: { type: String, default: 'blue' },
    sticker: { type: String, default: '' },
    tapeTone: { type: String, default: '' },
    stickerTone: { type: String, default: '' },
    inlineSticker: { type: Boolean, default: false },
  },
  setup() {
    return { detailBubble, detailFlower, communitySparkle }
  },
})
