import assert from 'node:assert/strict'
import { chromium } from 'playwright'

// 独立 Playwright 回归使用接口回放，不调用模型或业务写接口；真实页面联调另用 Edge 控制工具。
const browser = await chromium.launch({ channel: 'msedge', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } })
const errors = [], posts = [], changes = []
page.on('pageerror', (error) => errors.push(error.message))
page.on('console', (message) => { if (message.type() === 'error') errors.push(message.text()) })
const now = '2026-10-17T04:00:00Z'
const usage = { messages: 198, documents: 10, images: 3, chatSeconds: 100, cooldownSeconds: 0 }
const characters = ['TSUNDERE','GENKI','NEKO'].map((code) => ({
  code, name: {TSUNDERE:'白濑凛',GENKI:'日向小葵',NEKO:'奶糖'}[code], tagline: '一起聊聊今天的事',
  stage: 3, stageName:'朋友', stageNames:['初识','熟悉','朋友','亲近','羁绊'], progress:0.3,
  level:2, levelNames:['正常','撒娇','撒娇卖萌'], acquaintanceDays:8, chatDays:6,
}))
let sessions = [{id:1,characterCode:'NEKO',title:'今天的小事',lastMessageTime:now,unread:false,generating:false},
  {id:2,characterCode:'NEKO',title:'另一个话题',lastMessageTime:now,unread:true,generating:false}]
let memories = [{id:1,kind:'LIKE',content:'喜欢蓝色',pinned:false},
  {id:2,kind:'PROMISE',content:'下周去看展览',pinned:false,dueDate:'2026-10-20'},
  {id:3,kind:'SELF',content:'喜欢晒太阳',pinned:false}]
const work = {id:10,characterCode:'NEKO',sessionId:1,kind:'DOCUMENT',title:'云朵小故事',body:'# 一朵云\n\n慢慢飘过屋顶。',createTime:now}
let messages = [{id:1,role:'USER',content:'今天有点累',kind:'TEXT',imageIds:[],turnState:'COMMITTED',createTime:now},
  {id:2,role:'CHARACTER',content:'这边让你一点，先歇会儿喵。',kind:'TEXT',imageIds:[],createTime:now},
  {id:3,role:'CHARACTER',content:'云朵小故事',workId:10,kind:'TEXT',imageIds:[],createTime:now},
  {id:4,role:'CHARACTER',content:'',kind:'STICKER',sticker:'好耶',imageIds:[],createTime:now}]
let next = 20, firstPending = true, forget = null, queue = []
const sse = (events) => events.map(([event,data]) => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`).join('')
const delay = (ms) => new Promise((resolve) => setTimeout(resolve,ms))
await page.route((url) => /^\/(?:api\/)?(companion|user|growth|im|content)\//.test(url.pathname), async (route) => {
  const request=route.request(), url=new URL(request.url()), path=url.pathname.replace(/^\/api/,''), method=request.method()
  const ok = (data) => route.fulfill({json:{code:'OK',data}})
  if (!path.startsWith('/companion/')) {
    if (path==='/user/me') return ok({id:41,nickname:'回放用户',role:'USER',level:3})
    if (path.includes('unread')) return ok({chat:0,comment:0,like:0,system:0})
    if (path.includes('summary')) return ok({level:3,experience:100})
    return ok({records:[],total:0})
  }
  if (!/\/file$|\/images\//.test(path)) assert.ok(request.headers()['x-time-zone'],'Missing timezone header')
  if (path==='/companion/characters') return ok(characters)
  if (/\/characters\/[^/]+\/level$/.test(path)) {
    const value=request.postDataJSON();characters.find(c=>c.code===path.split('/')[3]).level=value.level;changes.push(value);return ok(null)
  }
  if (/\/characters\/[^/]+\/sessions$/.test(path)) return ok({records:sessions,total:sessions.length})
  if (method==='DELETE' && /\/sessions\/\d+$/.test(path)) {forget=url.searchParams.get('forget');sessions=sessions.filter(s=>s.id!==Number(path.split('/').at(-1)));return ok(null)}
  if (/\/sessions\/\d+\/messages$/.test(path) && method==='GET') return ok({records:messages.toReversed(),nextBefore:null})
  if (/\/sessions\/\d+\/messages$/.test(path) && method==='POST') {
    assert.match(request.headers().accept,/text\/event-stream/)
    assert.match(request.headers().accept,/application\/json/)
    if (path.includes('/999999/')) return route.fulfill({status:429,json:{code:'COMPANION_COOLDOWN',data:{cooldownSeconds:42}}})
    const body=request.postDataJSON();posts.push(body);assert.match(body.clientMessageId,/^[a-f0-9-]{36}$/)
    const user={id:next++,role:'USER',kind:'TEXT',content:body.content,clientMessageId:body.clientMessageId,imageIds:[],turnState:'RECEIVED',createTime:now}
    messages.push(user);sessions[0].generating=true
    if (!firstPending) { queue.push(user);return route.fulfill({contentType:'text/event-stream',body:sse([['queued',{clientMessageId:body.clientMessageId}],['done',{usage}]])}) }
    firstPending=false
    await delay(1600)
    user.turnState='COMMITTED'
    const reply={id:next++,role:'CHARACTER',content:'听见啦，慢慢说喵。',kind:'TEXT',imageIds:[],createTime:now}
    messages.push(reply)
    for (const item of queue) item.turnState='COMMITTED'
    queue=[];sessions[0].generating=false
    return route.fulfill({contentType:'text/event-stream',body:sse([['bubble',reply],['done',{usage}]])})
  }
  if (/\/messages\/\d+\/feedback$/.test(path)) {changes.push('feedback');return ok(null)}
  if (/\/messages\/\d+\/regeneration$/.test(path)) {
    assert.match(request.headers().accept,/text\/event-stream/)
    assert.match(request.headers().accept,/application\/json/)
    if (path.includes('/999999/')) return route.fulfill({status:404,json:{code:'COMMON_NOT_FOUND',message:'话题不存在'}})
    const oldId=Number(path.split('/')[3]), reply={id:next++,role:'CHARACTER',content:'水放旁边了，先歇一会儿喵。',kind:'TEXT',imageIds:[],createTime:now}
    messages=messages.filter(m=>m.id!==oldId);messages.push(reply)
    return route.fulfill({contentType:'text/event-stream',body:sse([['bubble',reply],['done',{usage}]])})
  }
  if (/\/characters\/[^/]+\/memories$/.test(path)) return ok(memories)
  if (/\/memories\/\d+$/.test(path) && method==='PATCH') {const body=request.postDataJSON();Object.assign(memories.find(m=>m.id===Number(path.split('/').at(-1))),body);changes.push(body);return ok(null)}
  if (path.endsWith('/calendar')) return route.fulfill({contentType:'text/calendar',headers:{'content-disposition':'attachment; filename=promise.ics'},body:'BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n'})
  if (path==='/companion/works') return ok({records:[work],total:1})
  if (/\/works\/\d+$/.test(path)) return ok(work)
  if (path==='/companion/usage') return ok(usage)
  throw new Error(`Unexpected companion request ${method} ${path}`)
})
await page.routeWebSocket('**/im/**', () => {})
try {
  await page.goto(new URL('/meng/neko?s=1',process.env.MENG_FRONT_USER_URL || 'http://localhost:5173').href)
  await page.getByRole('group',{name:'性格档位'}).waitFor()
  // 直接调用实际 API 模块，验证两个 POST 入口都能保留 JSON 业务码、状态与附加数据。
  const failures = await page.evaluate(async () => {
    const api = await import('/src/api/companion.js')
    const options = {signal:new AbortController().signal,onEvent:async()=>{throw new Error('JSON failure must not emit SSE')}}
    const send = await api.sendCompanionMessage(999999,{clientMessageId:crypto.randomUUID(),content:'错误协商验证'},options).catch(error=>error)
    const regenerate = await api.regenerateCompanionMessage(999999,options).catch(error=>error)
    return {send,regenerate}
  })
  assert.equal(failures.send.code,'COMPANION_COOLDOWN');assert.equal(failures.send.status,429)
  assert.equal(failures.send.data.cooldownSeconds,42)
  assert.equal(failures.regenerate.code,'COMMON_NOT_FOUND');assert.equal(failures.regenerate.status,404)
  assert.ok(failures.regenerate.message)
  // 浏览器会为上述有意触发的 HTTP 失败输出资源错误；其余错误继续作为失败依据。
  for (let i=errors.length-1;i>=0;i--) if (/Failed to load resource:.*(?:404|429)/.test(errors[i])) errors.splice(i,1)
  const grid = page.locator('canvas.shape-grid')
  await page.waitForFunction(() => document.querySelector('canvas.shape-grid')?.width > 0)
  assert.equal(await grid.getAttribute('aria-hidden'),'true')
  assert.equal(await grid.evaluate((canvas) => getComputedStyle(canvas).pointerEvents),'none')
  const firstGrid = await grid.evaluate((canvas) => canvas.toDataURL())
  await delay(250)
  assert.notEqual(await grid.evaluate((canvas) => canvas.toDataURL()),firstGrid,'Grid should move slowly')
  await page.emulateMedia({reducedMotion:'reduce'})
  await delay(100)
  const staticGrid = await grid.evaluate((canvas) => canvas.toDataURL())
  await delay(250)
  assert.equal(await grid.evaluate((canvas) => canvas.toDataURL()),staticGrid,'Reduced motion should keep the grid static')
  await page.emulateMedia({reducedMotion:'no-preference'})
  assert.match(page.url(),/\/meng\/neko/);assert.ok(await page.title())
  assert.ok(await page.locator('body').innerText());assert.equal(await page.locator('vite-error-overlay').count(),0)
  await page.getByRole('button',{name:'撒娇卖萌',exact:true}).click()
  await page.waitForFunction(() => document.querySelector('.level-switch button:last-child')?.getAttribute('aria-pressed')==='true')
  assert.equal(changes[0].level,3)
  await page.getByRole('img',{name:'琪露诺贴纸：好耶'}).waitFor()
  await page.getByRole('button',{name:'续写',exact:true}).first().click()
  await page.getByText('正在续写：云朵小故事',{exact:true}).waitFor()
  const input=page.locator('.chat-input-area textarea'), send=page.getByRole('button',{name:'发送',exact:true}).last()
  await input.fill('把故事续写一下');await send.click()
  await input.fill('结尾加一阵小雨');await send.click()
  await page.getByText('已排队',{exact:true}).first().waitFor()
  assert.equal(posts[0].workId,10);assert.notEqual(posts[0].clientMessageId,posts[1].clientMessageId)
  await delay(2800)
  const firstBubble=page.locator('.message-bubble').filter({hasText:'这边让你一点'}).first()
  await firstBubble.hover();await firstBubble.getByRole('button',{name:'有点出戏'}).click()
  assert.ok(changes.includes('feedback'))
  await firstBubble.getByRole('button',{name:'换一句'}).click()
  await page.getByText('水放旁边了，先歇一会儿喵。',{exact:true}).waitFor()
  await page.getByRole('button',{name:'全部 3 条',exact:true}).click()
  await page.getByRole('heading',{name:'喜好',exact:true}).waitFor()
  const like=page.locator('.memory-groups section').filter({has:page.getByRole('heading',{name:'喜好',exact:true})}).locator('li').first()
  await like.getByRole('button',{name:'编辑记忆',exact:true}).click()
  await like.getByRole('textbox',{name:'编辑记忆'}).fill('喜欢浅蓝色')
  await like.getByRole('button',{name:'保存记忆'}).click()
  const edited=page.locator('.memory-grid li').filter({hasText:'喜欢浅蓝色'})
  await edited.getByRole('button',{name:'置顶记忆',exact:true}).click()
  await edited.getByRole('button',{name:'取消置顶',exact:true}).waitFor()
  const download=page.waitForEvent('download')
  await page.getByRole('button',{name:'加到日历'}).click()
  assert.equal((await download).suggestedFilename(),'promise.ics')
  const output=process.env.MENG_COMPANION_SCREENSHOT
  if (output) await page.screenshot({path:output,fullPage:false})
  await page.keyboard.press('Escape')
  await page.locator('.topic-row').filter({hasText:'另一个话题'}).getByRole('button',{name:'删除话题'}).click()
  await page.getByRole('checkbox',{name:'连带忘掉这段对话里记住的事'}).check()
  await page.getByRole('button',{name:'确定',exact:true}).click()
  await delay(300);assert.equal(forget,'true')
  await page.setViewportSize({width:390,height:844});await delay(200)
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth<=window.innerWidth),'Mobile horizontal overflow')
  if (output) await page.screenshot({path:output.replace('.png','-mobile.png'),fullPage:false})
  assert.deepEqual(errors,[])
  console.log('PASS: grid animation/reduced motion, timezone, levels, stickers, work continuation, queueing, feedback, regeneration, memory editing/pinning, calendar, forget option and mobile layout')
} catch(error) { console.error('UI errors:',errors);console.error('Rendered:',(await page.locator('body').innerText()).slice(0,3000));throw error } finally {await browser.close()}
