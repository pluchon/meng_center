import { chromium } from 'playwright'
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const output = new URL('../test-output/visual-polish/', import.meta.url)
await fs.mkdir(output, { recursive:true })
const browser=await chromium.launch({channel:'msedge',headless:true})
const page=await browser.newPage({viewport:{width:1366,height:768}})
const report={scope:'Only reset presentation, particles and cursor; no backend or complete regression',browser:'Edge / Playwright; Browser plugin not available',checks:[],errors:[],passed:false}
page.on('pageerror',e=>report.errors.push(e.message))
page.on('console',e=>{if(e.type()==='error'&&!e.text().includes('401 (Unauthorized)'))report.errors.push(e.text())})
const glow=()=>page.locator('.glow-cursor')
const ink=()=>glow().evaluate(canvas=>{const pixels=canvas.getContext('2d').getImageData(0,0,canvas.width,canvas.height).data;let count=0;for(let i=3;i<pixels.length;i+=4)if(pixels[i])count++;return count})
try {
 await page.goto('http://127.0.0.1:5173/reset-password');await page.locator('h1').waitFor();await page.evaluate(()=>document.fonts.ready)
 assert.match(await page.title(),/重置密码/)
 assert.equal(await page.getByText('用注册时的邮箱或手机号，找回你的账号。',{exact:true}).count(),0)
 // 头像在构建时缩小转码，地址不再带原文件名，改为确认图片确实解码成功
 await page.locator('.reset-avatar').evaluate(img=>img.decode())
 assert.ok(await page.locator('.reset-avatar').evaluate(img=>img.naturalWidth>0))
 assert.match(await page.locator('.reset-action').evaluate(el=>getComputedStyle(el).fontFamily),/Noto Sans SC/)
 assert.equal(await page.locator('vite-error-overlay').count(),0)
 report.checks.push('new avatar, no subtitle, clearer title; page identity and no framework overlay')
 await page.mouse.move(130,210)
 for(let i=0;i<25;i++){await page.mouse.move(130+i*15,210+Math.sin(i*.12)*55);await page.waitForTimeout(10)}
 await page.waitForTimeout(30)
 assert.ok(await ink()>100)
 assert.equal(await glow().evaluate(el=>getComputedStyle(el).pointerEvents),'none')
 await page.screenshot({path:new URL('reset-desktop.png',output).pathname.replace(/^\//,'')})
 await page.getByRole('button',{name:'切换为手机号',exact:true}).click();assert.equal(await page.locator('.contact-region').count(),1)
 await page.waitForTimeout(1300);assert.equal(await ink(),0)
 report.checks.push('luminous trail renders, leaves controls clickable, fades completely while idle')
 const graph=await page.evaluate(async()=>{
  const {buildConnections,advanceDispersion}=await import('/src/components/particle-background/particle-network.js')
  const points=Array.from({length:85},()=>({x:500,y:400,neighbors:new Set(),excluded:new Set(),disperseTime:0}))
  const network=buildConnections(points,()=>false)
  const pair=[{x:0,y:0,neighbors:new Set([1])},{x:160,y:0,neighbors:new Set([0])}]
  const stable=buildConnections(pair,()=>false).links.length
  pair.forEach(point=>point.neighbors.clear())
  const fresh=buildConnections(pair,()=>false).links.length
  const point={x:0,y:0,spreadX:1,spreadY:0,disperseTime:2}
  advanceDispersion(point,.016)
  return {degree:Math.max(...network.neighbors.map(set=>set.size)),component:Math.max(...network.components.map(group=>group.length)),edges:network.links.length,stable,fresh,firstStep:point.x}
 })
 assert.ok(graph.degree<=3&&graph.component<=8&&graph.edges<=127)
 assert.equal(graph.stable,1);assert.equal(graph.fresh,0);assert.ok(graph.firstStep>0&&graph.firstStep<.1)
 report.checks.push({name:'bounded graph, connection hysteresis and smooth dispersion acceleration',graph})
 const frameTiming=await page.evaluate(async()=>{const samples=[];let previous=performance.now();await new Promise(resolve=>{function sample(now){samples.push(now-previous);previous=now;if(samples.length<90)requestAnimationFrame(sample);else resolve()}requestAnimationFrame(sample)});samples.shift();samples.sort((a,b)=>a-b);return {medianMilliseconds:samples[Math.floor(samples.length*.5)],p95Milliseconds:samples[Math.floor(samples.length*.95)]}})
 report.checks.push({name:'observed frame timings (environment dependent)',frameTiming})
 await page.emulateMedia({reducedMotion:'reduce'});await page.mouse.move(500,400);await page.waitForTimeout(60)
 assert.equal(await glow().isVisible(),false);assert.equal(await ink(),0)
 const canvas=page.locator('.particle-background'),still=await canvas.evaluate(el=>el.toDataURL());await page.waitForTimeout(100);assert.equal(await canvas.evaluate(el=>el.toDataURL()),still)
 report.checks.push('reduced motion hides cursor and freezes particles')
 await page.emulateMedia({reducedMotion:'no-preference'})
 await page.setViewportSize({width:375,height:667});await page.mouse.move(0,0)
 await page.screenshot({path:new URL('reset-mobile.png',output).pathname.replace(/^\//,'')})
 const dimensions=await page.evaluate(()=>({w:innerWidth,h:innerHeight,sw:document.documentElement.scrollWidth,sh:document.documentElement.scrollHeight}))
 assert.ok(dimensions.sw<=dimensions.w+1&&dimensions.sh<=dimensions.h+1)
 const touch=await browser.newContext({viewport:{width:375,height:667},isMobile:true,hasTouch:true});const mobile=await touch.newPage();await mobile.goto('http://127.0.0.1:5173/reset-password');await mobile.locator('h1').waitFor();assert.equal(await mobile.locator('.glow-cursor').isVisible(),false);await touch.close()
 await page.goto('http://127.0.0.1:5173/login');await page.locator('h1').waitFor();assert.equal(await glow().count(),1)
 assert.deepEqual(report.errors,[])
 report.checks.push({name:'mobile no overflow, touch disables glow, one shared cursor after navigation',dimensions})
 report.passed=true
} catch(e){report.failure=e.message;throw e}
finally{await fs.writeFile(new URL('report.json',output),JSON.stringify(report,null,2));await browser.close();console.log(JSON.stringify(report,null,2))}
