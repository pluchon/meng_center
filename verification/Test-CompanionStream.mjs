import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readCompanionStream } from '../vue_front/front_user/src/api/companionStream.js'

const encoder = new TextEncoder()
function stream(chunks, cancel = () => {}) {
  return new ReadableStream({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(chunk)
      controller.close()
    },
    cancel,
  })
}
async function collect(chunks) {
  const events = []
  await readCompanionStream(stream(chunks), {
    signal: new AbortController().signal,
    onEvent: async (name, data) => { events.push([name, data]) },
  })
  return events
}

test('中文和 CRLF 在每一个字节边界处分块仍按顺序还原完整气泡', async () => {
  const bytes = encoder.encode('event: bubble\r\ndata: {"content":"你好，奶糖～"}\r\n\r\nevent: done\r\ndata: {"usage":{"messages":199}}\r\n\r\n')
  const result = await collect(Array.from(bytes, (byte) => Uint8Array.of(byte)))
  assert.deepEqual(result, [
    ['bubble', { content: '你好，奶糖～' }],
    ['done', { usage: { messages: 199 } }],
  ])
})

test('心跳注释和多行 data 不产生额外气泡', async () => {
  const result = await collect([encoder.encode(': heartbeat\n\nevent: status\ndata: {\ndata: "text":"正在翻帖子"}\n\nevent: done\ndata: {}\n\n')])
  assert.deepEqual(result, [['status', { text: '正在翻帖子' }], ['done', {}]])
})

test('缺少结束事件的断流不能当作成功', async () => {
  await assert.rejects(collect([encoder.encode('event: bubble\ndata: {"content":"半途断开"}\n\n')]), (error) => error.code === 'NETWORK')
})

test('业务错误不会留下锁定的流读取器', async () => {
  const body = stream([encoder.encode('event: error\ndata: {"code":"AI_UNAVAILABLE"}\n\n')])
  await assert.rejects(readCompanionStream(body, {
    signal: new AbortController().signal,
    onEvent: async (_, data) => { throw data },
  }), (error) => error.code === 'AI_UNAVAILABLE')
  assert.equal(body.locked, false)
})

test('上一条气泡仍在展示时不会提前消费后续事件', async () => {
  const order = []
  let release
  const gate = new Promise((resolve) => { release = resolve })
  const pending = readCompanionStream(stream([encoder.encode('event: bubble\ndata: {}\n\nevent: done\ndata: {}\n\n')]), {
    signal: new AbortController().signal,
    onEvent: async (name) => {
      order.push('begin:' + name)
      if (name === 'bubble') await gate
      order.push('end:' + name)
    },
  })
  await new Promise((resolve) => setImmediate(resolve))
  assert.deepEqual(order, ['begin:bubble'])
  release()
  await pending
  assert.deepEqual(order, ['begin:bubble', 'end:bubble', 'begin:done', 'end:done'])
})
