import { computed, defineComponent } from 'vue'
import { commentSegments } from '@/constants/comments'
export default defineComponent({
  name: 'CommentBody',
  props: {
    content: { type: String, default: '' },
    // 正文里 @ 到的人，昵称是发出评论时的昵称
    mentions: { type: Array, default: () => [] },
  },
  setup(props) {
    const segments = computed(() => commentSegments(props.content))
    const texts = computed(() => segments.value.filter((item) => !item.image))
    // 把文字按 @昵称 切开，被 @ 的部分单独高亮
    const parts = computed(() => {
      const text = texts.value.map((item) => item.text).join('')
      const names = props.mentions
        .map((item) => '@' + item.nickname)
        .sort((a, b) => b.length - a.length)
      if (!names.length) return [{ text, mention: false }]
      const pattern = new RegExp(
        '(' + names.map((name) => name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|') + ')',
        'u',
      )
      return text
        .split(pattern)
        .filter(Boolean)
        .map((part) => ({ text: part, mention: names.includes(part) }))
    })
    return {
      texts,
      parts,
      images: computed(() => segments.value.filter((item) => item.image)),
    }
  },
})
