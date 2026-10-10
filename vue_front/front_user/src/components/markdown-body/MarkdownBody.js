import { computed, defineComponent } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import java from 'highlight.js/lib/languages/java'
import python from 'highlight.js/lib/languages/python'
import json from 'highlight.js/lib/languages/json'
import css from 'highlight.js/lib/languages/css'
import xml from 'highlight.js/lib/languages/xml'
import sql from 'highlight.js/lib/languages/sql'
for (const [name, language] of Object.entries({ javascript, java, python, json, css, xml, sql }))
  hljs.registerLanguage(name, language)
const markdown = new MarkdownIt({
  html: false,
  linkify: false,
  breaks: true,
  highlight: (code, language) =>
    hljs.getLanguage(language)
      ? hljs.highlight(code, { language, ignoreIllegals: true }).value
      : '',
}).disable('image')
markdown.validateLink = (url) => /^https?:\/\//i.test(url)
markdown.renderer.rules.link_open = (tokens, index, options, env, renderer) => {
  tokens[index].attrSet('target', '_blank')
  tokens[index].attrSet('rel', 'noopener noreferrer nofollow')
  return renderer.renderToken(tokens, index, options)
}
export default defineComponent({
  name: 'MarkdownBody',
  props: { content: { type: String, default: '' } },
  setup(props) {
    return {
      html: computed(() =>
        DOMPurify.sanitize(markdown.render(props.content), {
          USE_PROFILES: { html: true },
          ADD_ATTR: ['target', 'rel'],
          FORBID_TAGS: ['img', 'video', 'audio', 'iframe', 'style'],
          SANITIZE_NAMED_PROPS: true,
        }),
      ),
    }
  },
})
