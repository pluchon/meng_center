import { chromium } from 'playwright'
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const output = new URL('../test-output/reset/', import.meta.url)
await fs.mkdir(output, { recursive: true })
const browser = await chromium.launch({ channel: 'msedge', headless: true })
const page = await browser.newPage({ viewport: { width: 1366, height: 768 } })
const report = { checks: [], errors: [], passed: false }
page.on('pageerror', e => report.errors.push(e.message))
page.on('console', e => { if(e.type() === 'error' && !/(?:401 \(Unauthorized\)|400 \(Bad Request\))/.test(e.text())) report.errors.push(e.text()) })
const go = async () => { await page.goto('http://127.0.0.1:5173/reset-password'); await page.locator('h1').waitFor(); await page.evaluate(() => document.fonts.ready); await page.locator('.reset-avatar').evaluate(img => img.decode()) }
try {
 for (const viewport of [{width:1366,height:768},{width:390,height:844},{width:375,height:667}]) {
  await page.setViewportSize(viewport); await go()
  assert.equal(await page.getByRole('link', {name:'返回登录',exact:true}).count(), 1)
  assert.equal(await page.locator('.cloud, .wave, .poster').count(), 0)
  assert.equal(await page.locator('.particle-background').count(),1)
  const dimensions = await page.evaluate(() => ({w:innerWidth,h:innerHeight,sw:document.documentElement.scrollWidth,sh:document.documentElement.scrollHeight}))
  assert.ok(dimensions.sw<=dimensions.w+1 && dimensions.sh<=dimensions.h+1, JSON.stringify(dimensions))
  if(viewport.width>760) assert.equal(Math.round((await page.locator('.reset-card').boundingBox()).width),640)
  const code=await page.locator('.code-field .el-input').boundingBox(),send=await page.locator('.code-send').boundingBox()
  assert.ok(send.x>=code.x && send.x+send.width<=code.x+code.width)
  await page.screenshot({path:new URL(`reset-${viewport.width}x${viewport.height}.png`,output).pathname.replace(/^\//,'')})
  report.checks.push({name:'reset layout, avatar, single return link and inline code control',dimensions})
 }
 await page.setViewportSize({width:1366,height:768}); await go()
 await page.getByPlaceholder('请输入邮箱地址').fill('fixture@example.invalid')
 await page.getByRole('button',{name:'切换为手机号',exact:true}).click()
 assert.equal(await page.getByRole('button',{name:'切换为邮箱',exact:true}).innerText(),'')
 assert.match(await page.locator('.contact-region').innerText(),/台湾.*\+886/)
 await page.getByPlaceholder('请输入当地手机号').fill('912345678')
 await page.getByRole('button',{name:'切换为邮箱',exact:true}).click()
 assert.equal(await page.getByPlaceholder('请输入邮箱地址').inputValue(),'fixture@example.invalid')
 await page.getByRole('button',{name:'重置密码',exact:true}).click()
 await page.getByRole('alert').filter({hasText:'请输入 6 位验证码'}).waitFor()
 report.checks.push('registration contact toggle reused, values preserved, validation blocks premature submit')
 let sent, reset, failReset=true
 const image='data:image/svg+xml,'+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="320" height="160"><rect width="320" height="160" fill="lightblue"/></svg>')
 await page.route('**/user/captcha',route=>route.fulfill({json:{code:'OK',data:{id:'fixture',backgroundImage:image,templateImage:image,backgroundImageWidth:320,backgroundImageHeight:160,templateImageWidth:40}}}))
 await page.route('**/user/captcha/verification',route=>route.fulfill({json:{code:'OK',data:{ticket:'verification-ticket'}}}))
 await page.route('**/user/password-reset-codes',route=>{sent=route.request().postDataJSON();return route.fulfill({json:{code:'OK',data:null}})})
 await page.route('**/user/password-resets',route=>{reset=route.request().postDataJSON();return route.fulfill({status:failReset?400:200,json:failReset?{code:'USER_RESET_FAILED',message:'generic'}:{code:'OK',data:null}})})
 async function slide(){ const knob=page.locator('.slider-knob');await knob.waitFor();const b=await knob.boundingBox();await page.mouse.move(b.x+b.width/2,b.y+b.height/2);await page.mouse.down();await page.mouse.move(b.x+b.width/2+100,b.y+b.height/2,{steps:12});await page.mouse.up();await page.locator('.meng-captcha-dialog').waitFor({state:'hidden'}) }
 await page.locator('.code-send').click();await slide()
 await page.getByRole('button',{name:/秒后重发/}).waitFor();assert.equal(sent.email,'fixture@example.invalid');assert.equal(sent.ticket,'verification-ticket')
 await page.getByPlaceholder('请输入 6 位验证码').fill('123456')
 await page.getByPlaceholder('8–20位字母+数字').fill('reset123')
 await page.getByPlaceholder('再输入一次').fill('different1')
 await page.getByRole('button',{name:'重置密码',exact:true}).click()
 await page.getByRole('alert').filter({hasText:'两次输入的密码不一致'}).waitFor()
 await page.getByPlaceholder('再输入一次').fill('reset123')
 await page.getByRole('button',{name:'重置密码',exact:true}).click();await slide()
 await page.getByRole('alert').filter({hasText:'重置验证失败'}).waitFor()
 assert.equal(await page.getByPlaceholder('8–20位字母+数字').inputValue(),'reset123')
 failReset=false;await page.getByRole('button',{name:'重置密码',exact:true}).click();await slide()
 await page.getByRole('heading',{name:'密码重置成功',exact:true}).waitFor()
 assert.deepEqual(Object.keys(reset).sort(),['code','confirmPassword','email','password','ticket'])
 assert.equal(await page.locator('input[type=password]').count(),0)
 await page.getByRole('link',{name:'返回登录',exact:true}).click();await page.waitForURL('**/login')
 await page.getByRole('link',{name:'忘记密码',exact:true}).click();await page.waitForURL('**/reset-password')
 report.checks.push('mocked UI workflow: captcha, purpose-specific endpoints, countdown, mismatch, retry, success and login navigation')
 const graph = await page.evaluate(async()=>{
  const {buildConnections,disperseCrowds,advanceDispersion,advanceReconnection}=await import('/src/components/particle-background/particle-network.js')
  const {ParticleMotion:p}=await import('/src/constants/visual.js')
  const points=Array.from({length:p.MAX_POINTS},()=>({x:500,y:400,neighbors:new Set(),excluded:new Set(),reconnectTime:3,disperseTime:0,spreadX:0,spreadY:0}))
  const network=buildConnections(points,()=>false)
  network.neighbors.forEach((set,i)=>points[i].neighbors=set)
  const crowdPoints=Array.from({length:p.MAX_COMPONENT_POINTS},(_,i)=>({x:i*100,y:400,neighbors:new Set(),excluded:new Set(),reconnectTime:3,disperseTime:0,spreadX:0,spreadY:0}))
  const crowdNetwork=buildConnections(crowdPoints,()=>false)
  disperseCrowds(crowdPoints,crowdNetwork.components,.05)
  const point=crowdPoints[0],before={x:point.x,y:point.y}
  advanceReconnection(point,crowdPoints,.05,()=>false)
  const stopped=point.x===before.x&&point.y===before.y
  for(let i=0;i<10;i++) advanceDispersion(point,.05)
  const distance=Math.hypot(point.x-before.x,point.y-before.y)
  const next=buildConnections(points,()=>false)
  const blocked=buildConnections(points,()=>true)
  return {maxSize:Math.max(...network.components.map(group=>group.length)),maxDegree:Math.max(...network.neighbors.map(set=>set.size)),edges:network.links.length,maxEdges:Math.floor(p.MAX_POINTS*p.MAX_NEIGHBORS/2),stopped,distance,blocked:blocked.links.length,nextMax:Math.max(...next.components.map(group=>group.length))}
 })
 assert.ok(graph.maxSize<=8&&graph.nextMax<=8&&graph.maxDegree<=3&&graph.edges<=graph.maxEdges)
 assert.equal(graph.stopped,true);assert.ok(graph.distance>5);assert.equal(graph.blocked,0)
 report.checks.push({name:'85 overlapping particles: bounded components and edges; crowd attraction stops and dispersion moves outward',graph})
 assert.deepEqual(report.errors,[]);report.passed=true
} catch(e){ report.failure=e.message; throw e }
finally { await fs.writeFile(new URL('report.json',output),JSON.stringify(report,null,2));await browser.close();console.log(JSON.stringify(report,null,2)) }
