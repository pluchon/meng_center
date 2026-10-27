import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import { Companion, CompanionError, CompanionEvent } from '../vue_front/front_user/src/constants/companion.js'

const require = createRequire(new URL('../vue_front/front_user/package.json', import.meta.url))
const vue = require('vue')
const source = await readFile(new URL('../vue_front/front_user/src/components/naitang-assistant/NaitangAssistant.js', import.meta.url), 'utf8')
// 只替换模块边界，执行真实组件的 setup 和 Vue 响应式状态；不启动浏览器或业务服务。
const createComponent = new Function('dependencies', source
  .replace(/^import\s+([\s\S]*?)\s+from\s+['"]([^'"]+)['"]\s*$/gm,
    (_, names, module) => `const ${names} = dependencies[${JSON.stringify(module)}];`)
  .replace('export default defineComponent(', 'return defineComponent('))

function harness(send) {
  const sessions = [{ id: 1, generating: false }]
  const api = {
    sessions: async () => ({ records: sessions, total: 1 }),
    messages: async () => ({ records: [], nextBefore: null }),
    usage: async () => ({ messages: 10, cooldownSeconds: 0 }),
  }
  const dependencies = {
    vue,
    'vue-i18n': { useI18n: () => ({ t: (key) => key }) },
    'vue-router': { useRouter: () => ({}), useRoute: () => ({}) },
    '@tabler/icons-vue': {},
    '@/api/companion': { companionApi: api, companionImage: (id) => '/companion/images/' + id, sendCompanionMessage: send },
    '@/assets/images': { companionNekoAvatar: '' },
    '@/constants/companion': { Companion, CompanionError, CompanionEvent },
    '@/router/names': { RouteName: {} },
    '@/stores/user': { useUserStore: () => vue.reactive({ loggedIn: true }) },
    '@/stores/emotions': { useEmotionStore: () => ({ items: [] }) },
    '@/composables/useConfirm': { confirmDialog: async () => false },
  }
  const component = createComponent(dependencies)
  const renderer = vue.createRenderer({
    createComment: () => ({}), insert() {}, remove() {}, parentNode() {}, nextSibling() {},
  })
  let state
  const app = renderer.createApp({ setup() { state = component.setup(); return () => null } })
  app.mount({})
  state.selected.value = 1
  state.sessions.value = sessions
  return { state, dispose: () => app.unmount() }
}

test('失败恢复文字和图片，发送恢复的输入复用原编号且不重复添加消息', async () => {
  const bodies = []
  const instance = harness(async (_, body) => {
    bodies.push(body)
    if (bodies.length === 1) throw { code: CompanionError.UNAVAILABLE }
  })
  try {
    const state = instance.state
    state.input.value = '请看看图片'
    state.attachments.value = [{ id: 'image-1', preview: '/companion/images/image-1' }]
    await state.send()
    assert.equal(state.input.value, '请看看图片')
    assert.equal(state.attachments.value[0].id, 'image-1')
    assert.equal(state.messages.value.length, 1)
    await state.send()
    assert.equal(bodies.length, 2)
    assert.equal(bodies[0].clientMessageId, bodies[1].clientMessageId)
    assert.equal(state.input.value, '')
    assert.deepEqual(state.attachments.value, [])
  } finally { instance.dispose() }
})

test('失败不覆盖等待期间新输入的草稿', async () => {
  let reject
  let started
  const entered = new Promise((resolve) => { started = resolve })
  const instance = harness(() => new Promise((_, fail) => { reject = fail; started() }))
  try {
    const state = instance.state
    state.input.value = '上一条'
    const pending = state.send()
    await entered
    state.input.value = '下一条草稿'
    reject({ code: CompanionError.UNAVAILABLE })
    await pending
    assert.equal(state.input.value, '下一条草稿')
  } finally { instance.dispose() }
})

test('同一失败消息正在重试时，重复点击只发出一次请求', async () => {
  let resolve
  let calls = 0
  const instance = harness(() => { calls++; return new Promise((done) => { resolve = done }) })
  try {
    const message = { failed: true, retryBody: { clientMessageId: 'original', content: '原消息', imageIds: [] } }
    const state = instance.state
    const pending = state.retry(message)
    await state.retry(message)
    assert.equal(calls, 1)
    resolve()
    await pending
  } finally { instance.dispose() }
})
