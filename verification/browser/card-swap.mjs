import {chromium} from 'playwright'
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const out=new URL('../test-output/card-swap/',import.meta.url);await fs.mkdir(out,{recursive:true})
const browser=await chromium.launch({channel:'msedge',headless:true}),page=await browser.newPage({viewport:{width:1366,height:768}})
const report={scope:'Contact spacing and auth card navigation only',passed:false,errors:[],checks:[]}
page.on('pageerror',e=>report.errors.push(e.message))
page.on('console',e=>{if(e.type()==='error'&&!e.text().includes('401 (Unauthorized)'))report.errors.push(e.text())})
const settled=()=>page.waitForFunction(()=>document.querySelector('.card-swap-stage')?.getAttribute('aria-busy')==='false')
async function alignment(){await page.getByRole('button',{name:'切换为邮箱',exact:true}).click();const offsets=await page.evaluate(()=>{const email=document.querySelector('.email-control'),code=document.querySelector('.code-field .el-input');return {email:email.querySelector('input').getBoundingClientRect().x-email.getBoundingClientRect().x,code:code.querySelector('input').getBoundingClientRect().x-code.getBoundingClientRect().x}});assert.ok(Math.abs(offsets.email-offsets.code)<1,JSON.stringify(offsets));return offsets}
try{
 await page.goto('http://127.0.0.1:5173/register');await page.locator('h1').waitFor();await page.evaluate(()=>document.fonts.ready)
 report.checks.push({name:'email padding matches ordinary code input',offsets:await alignment()})
 await page.getByRole('link',{name:'返回登录',exact:true}).click();await page.waitForURL('**/login');await settled()
 await page.getByRole('link',{name:'申请加入',exact:true}).click();await page.waitForURL('**/register')
 await page.waitForTimeout(160)
 assert.equal(await page.locator('.card-swap-stage').getAttribute('aria-busy'),'true')
 assert.equal(await page.locator('.auth-card').count(),2)
 assert.equal(await page.locator('.particle-background').count(),1)
 const ghost=page.locator('.auth-card[aria-hidden=true]')
 assert.equal(await ghost.evaluate(el=>el.inert),true)
 assert.ok(await ghost.evaluate(el=>el.getAnimations().length>0))
 await page.screenshot({path:new URL('card-swap-midpoint.png',out).pathname.replace(/^\//,'')})
 await settled();assert.equal(await page.locator('.auth-card').count(),1)
 assert.equal(await page.locator('.auth-card').evaluate(el=>el.inert),false)
 assert.equal(await page.locator('.glow-cursor').count(),1)
 assert.equal(await page.locator('h1').evaluate(el=>document.activeElement===el),true)
 await page.screenshot({path:new URL('register-aligned.png',out).pathname.replace(/^\//,'')})
 report.checks.push('card lifts into rear layer; single persistent particle canvas; snapshot inert and removed; focus restored')
 await page.getByRole('link',{name:'返回登录',exact:true}).click();await page.waitForURL('**/login');await page.waitForTimeout(90)
 await page.goBack();await page.waitForURL('**/register');await settled()
 assert.equal(await page.locator('.auth-card').count(),1)
 report.checks.push('rapid browser navigation cancels prior animations and clears snapshots')
 await page.emulateMedia({reducedMotion:'reduce'})
 await page.getByRole('link',{name:'返回登录',exact:true}).click();await page.waitForURL('**/login')
 assert.equal(await page.locator('.card-swap-stage').getAttribute('aria-busy'),'false')
 assert.equal(await page.locator('.auth-card').count(),1)
 await page.setViewportSize({width:375,height:667})
 await page.getByRole('link',{name:'申请加入',exact:true}).click();await page.waitForURL('**/register');await settled()
 await alignment()
 const size=await page.evaluate(()=>({w:innerWidth,h:innerHeight,sw:document.documentElement.scrollWidth,sh:document.documentElement.scrollHeight}))
 assert.ok(size.sw<=size.w+1&&size.sh<=size.h+1,JSON.stringify(size))
 report.checks.push('reduced motion switches immediately; mobile padding matches and no page overflow')
 assert.deepEqual(report.errors,[]);report.passed=true
}catch(e){report.failure=e.message;throw e}
finally{await fs.writeFile(new URL('report.json',out),JSON.stringify(report,null,2));await browser.close();console.log(JSON.stringify(report,null,2))}
