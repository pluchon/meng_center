import { defineComponent } from 'vue'
import { useI18n } from 'vue-i18n'

// 帖子分类的小吊牌：首页卡片、帖子详情和相关推荐共用
export default defineComponent({
  name: 'CategoryTag',
  props: { category: { type: String, required: true } },
  setup() {
    return useI18n()
  },
})
