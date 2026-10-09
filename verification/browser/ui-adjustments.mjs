import { chromium } from 'playwright'
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { solveCaptcha } from './captcha.mjs'
const output = fileURLToPath(new URL('../test-output/ui-adjustments/', import.meta.url))
await fs.mkdir(output, { recursive: true })
const browser = await chromium.launch({ channel: 'msedge', headless: true })
const page = await browser.newPage({ viewport: { width: 1366, height: 768 } })
await page.addInitScript(() => {
 const prototype = CanvasRenderingContext2D.prototype
 const clear = prototype.clearRect, arc = prototype.arc, stroke = prototype.stroke, move = prototype.moveTo, line = prototype.lineTo
 prototype.clearRect = function(...args) { if(this.canvas.classList.contains('particle-background')) window.particleSample = { points: [], segments: [] }; return clear.apply(this,args) }
 prototype.arc = function(...args) { if(this.canvas.classList.contains('particle-background')) window.particleSample?.points.push(args.slice(0,2)); return arc.apply(this,args) }
 prototype.moveTo = function(x,y) { this.verificationStart=[x,y]; return move.call(this,x,y) }
 prototype.lineTo = function(x,y) { this.verificationEnd=[x,y]; return line.call(this,x,y) }
 prototype.stroke = function(...args) { if(this.canvas.classList.contains('particle-background')) window.particleSample?.segments.push([this.verificationStart,this.verificationEnd]); return stroke.apply(this,args) }
})
const report = { browser: 'Edge / Playwright', fallback: 'Browser plugin not available', checks: [], errors: [], passed: false }
page.on('pageerror', e => report.errors.push(e.message))
// Unauthenticated /me returns an expected 401 during session restoration.
page.on('console', e => { if(e.type() === 'error' && !e.text().includes('401 (Unauthorized)') && !e.text().includes('403 (Forbidden)')) report.errors.push(e.text()) })
const go = async route => { await page.goto('http://127.0.0.1:5173/' + route); await page.locator('h1').waitFor(); await page.evaluate(() => document.fonts.ready) }
try {
 await go('login')
 let requests = 0
 await page.route('**/user/sessions', route => { requests++; return route.fulfill({ status: 403, contentType: 'application/json', body: JSON.stringify({ code: 'USER_PENDING', message: 'verification pending state' }) }) })
 await page.getByRole('button', { name: '登录', exact: true }).click()
 await page.getByRole('alert').filter({ hasText: '请先阅读并同意协议' }).waitFor()
 assert.equal(requests, 0)
 assert.equal(await page.getByRole('dialog').count(), 0)
 await page.locator('input[autocomplete="username"]').fill('verification_pending')
 await page.locator('input[autocomplete="current-password"]').fill('verification-password-1')
 await page.getByRole('checkbox').check()
 await solveCaptcha(page, () => page.getByRole('button', { name: '登录', exact: true }).click())
 await page.getByText('申请正在审核中', { exact: true }).waitFor()
 assert.equal(requests, 1)
 await page.getByRole('checkbox').uncheck()
 assert.equal(await page.getByRole('checkbox').isChecked(), false)
 assert.match(await page.getByRole('link', { name: '隐私政策', exact: true }).getAttribute('href'), /from=login/)
 assert.equal(await page.getByRole('link', { name: '隐私政策', exact: true }).getAttribute('target'), '_blank')
 await page.unroute('**/user/sessions')
 const forgot = page.getByRole('link', { name: '忘记密码', exact: true })
 assert.equal(await forgot.getAttribute('href'), '/reset-password')
 const forgotBox = await forgot.boundingBox(), optionsBox = await page.locator('.login-options').boundingBox()
 assert.ok(Math.abs(forgotBox.x + forgotBox.width - optionsBox.x - optionsBox.width) < 1)
 await page.locator('.auth-primary').hover()
 await page.waitForTimeout(60)
 assert.ok(await page.locator('.auth-primary').evaluate(button => button.getAnimations({ subtree: true }).length > 0))
 report.checks.push('forgot-password route at right of consent row; login button hover animation')
 report.checks.push('login consent blocks API; checking permits request (mocked pending response); legal links preserve original form')
 await go('register')
 assert.equal(await page.getByText('先认识一下，再一起分享喜欢的世界。').count(), 0)
 assert.match(await page.locator('.contact-region').innerText(), /台湾.*\+886/)
 await page.getByPlaceholder('请输入当地手机号').fill('988776654')
 await page.locator('.contact-region').click()
 await page.getByRole('option', { name: '日本 +81', exact: true }).click()
 await page.getByRole('button', { name: '切换为邮箱', exact: true }).click()
 await page.getByPlaceholder('请输入邮箱地址').fill('verification@example.invalid')
 assert.equal(await page.locator('.contact-region').count(), 0)
 const placeholderStyles = await page.evaluate(() => ['input[autocomplete=username]', 'input[autocomplete=email]'].map(selector => { const styles=getComputedStyle(document.querySelector(selector),'::placeholder'); return [styles.fontSize, styles.fontWeight, styles.color] }))
 assert.deepEqual(placeholderStyles[0],placeholderStyles[1])
 const firstPassword=page.locator('input[autocomplete=new-password]').first()
 assert.equal(await firstPassword.getAttribute('placeholder'), '8–20位字母+数字')
 assert.equal(await page.locator('.password-hint').count(),0)
 const codeBox=await page.locator('.code-field .el-input').boundingBox(),sendBox=await page.locator('.code-send').boundingBox()
 assert.ok(sendBox.x > codeBox.x && sendBox.x + sendBox.width < codeBox.x + codeBox.width)
 assert.ok(sendBox.y >= codeBox.y && sendBox.y + sendBox.height <= codeBox.y + codeBox.height)
 report.checks.push('password rules inside input; uniform email placeholder; code button contained in full-row input')
 await page.getByRole('button', { name: '切换为手机号', exact: true }).click()
 assert.equal(await page.getByPlaceholder('请输入当地手机号').inputValue(), '988776654')
 assert.match(await page.locator('.contact-region').innerText(), /日本.*\+81/)
 report.checks.push('merged contact field, region menu and email/mobile switching')
 assert.equal(await page.locator('.contact-switch').innerText(), '')
 await page.getByRole('button', { name: '切换为邮箱', exact: true }).click()
 await page.waitForTimeout(60)
 const transforms = await page.locator('.switch-icon').evaluate(icon => [...icon.querySelectorAll('path')].map(path => new DOMMatrixReadOnly(getComputedStyle(path).transform).m41))
 assert.ok(transforms[0] < 0 && transforms[1] > 0)
 await page.waitForTimeout(400)
 assert.equal(await page.locator('.contact-switch').evaluate(button => button.classList.contains('is-switching')), false)
 report.checks.push('icon-only switch: upper line enters from left and lower line from right')
 await page.locator('input[autocomplete=username]').fill('test_user')
 const passwords = page.locator('input[autocomplete=new-password]')
 assert.equal(await passwords.first().getAttribute('maxlength'), '20')
 for (const password of ['abc1234', 'abcdefgh', '12345678', 'abc1234!']) {
  await passwords.first().fill(password)
  await passwords.last().fill(password)
  await page.getByRole('button', { name: '提交申请', exact: true }).click()
  await page.getByRole('alert').filter({ hasText: '密码为 8～20 位' }).waitFor()
 }
 await passwords.first().fill('Abcd1234')
 await passwords.last().fill('Different123')
 await page.getByRole('button', { name: '提交申请', exact: true }).click()
 await page.getByRole('alert').filter({ hasText: '两次输入的密码不一致' }).waitFor()
 report.checks.push('registration password: length, letters plus digits, confirmation and maximum input length')
 await go('register')
 const editor = page.getByRole('textbox', { name: '申请理由', exact: true })
 assert.equal(await page.locator('textarea').count(), 0)
 await editor.fill('字'.repeat(100))
 assert.equal((await editor.innerText()).length, 100)
 await editor.press('End'); await editor.press('a')
 assert.equal((await editor.innerText()).length, 100)
 await editor.fill('')
 await editor.evaluate(el => { el.focus(); const data = new DataTransfer(); data.setData('text/plain', '粘贴'.repeat(80)); data.setData('text/html', '<img src=x>'); el.dispatchEvent(new ClipboardEvent('paste', { clipboardData: data, bubbles: true, cancelable: true })) })
 assert.equal((await editor.innerText()).length, 100)
 assert.equal(await editor.locator('img').count(), 0)
 await editor.evaluate(el => { el.dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true })); el.textContent = '中'.repeat(105); el.dispatchEvent(new InputEvent('input', { bubbles: true, isComposing: true })); el.dispatchEvent(new CompositionEvent('compositionend', { bubbles: true })) })
 assert.equal((await editor.innerText()).length, 100)
 await editor.fill('第一行\n第二行\n第三行\n第四行\n第五行')
 assert.equal(await editor.evaluate(el => el.scrollHeight > el.clientHeight), true)
 report.checks.push('100-character editor: keyboard, plain paste, composition and internal scroll')
 const box = await page.locator('.legal-consent .consent-checkbox').boundingBox(), text = await page.locator('.consent-copy').boundingBox()
 assert.ok(text.x > box.x + box.width && Math.abs(text.y + text.height / 2 - box.y - box.height / 2) <= 1)
 assert.match(await page.locator('link[rel=icon]').getAttribute('href'), /meng_center_ico/)
 await page.locator('.poster-brand img').evaluate(img => img.decode())
 report.checks.push('consent alignment and provided brand asset')
 const canvas = page.locator('.particle-background'), before = await canvas.evaluate(el => el.toDataURL())
 await page.waitForTimeout(150); assert.notEqual(await canvas.evaluate(el => el.toDataURL()), before)
 await page.emulateMedia({ reducedMotion: 'reduce' }); await page.waitForTimeout(50)
 const still = await canvas.evaluate(el => el.toDataURL())
 await page.waitForTimeout(150); assert.equal(await canvas.evaluate(el => el.toDataURL()), still)
 await page.emulateMedia({ reducedMotion: 'no-preference' })
 report.checks.push('animated particles and reduced-motion static frame')
 await page.mouse.move(680,760)
 await page.waitForTimeout(50)
 const sample = await page.evaluate(() => window.particleSample.points)
 const card = await page.locator('.auth-card').boundingBox()
 const targetIndex = sample.findIndex(([x,y]) => x > 10 && x < 1356 && y > 10 && y < 758 && (x < card.x - 8 || x > card.x + card.width + 8 || y < card.y - 8 || y > card.y + card.height + 8))
 assert.ok(targetIndex >= 0, 'a visible particle outside the card is available')
 const target = sample[targetIndex]
 await page.mouse.move(target[0], target[1])
 await page.waitForTimeout(250)
 const afterPointer = await page.evaluate(() => window.particleSample)
 assert.ok(Math.hypot(afterPointer.points[targetIndex][0]-target[0], afterPointer.points[targetIndex][1]-target[1]) > 10)
 for (const [start,end] of afterPointer.segments) {
  const dx=end[0]-start[0],dy=end[1]-start[1],length=dx*dx+dy*dy
  const t=length ? Math.max(0,Math.min(1,((target[0]-start[0])*dx+(target[1]-start[1])*dy)/length)) : 0
  assert.ok(Math.hypot(start[0]+dx*t-target[0],start[1]+dy*t-target[1]) >= 69)
 }
 report.checks.push('pointer repels nearby particles and breaks links within its radius')
 for(const viewport of [{ width:1366, height:768 }, { width:390, height:844 }, { width:375, height:667 }]) {
  await page.setViewportSize(viewport)
  for(const route of ['login','register','agreement']) {
   await go(route)
   const name = route + '-' + viewport.width + 'x' + viewport.height
   const d = await page.evaluate(() => ({ w: innerWidth, h: innerHeight, sw: document.documentElement.scrollWidth, sh: document.documentElement.scrollHeight }))
   await page.waitForTimeout(350)
   await page.screenshot({ path: output + '/' + name + '.png', fullPage:true })
   report.checks.push({ name, dimensions:d })
   assert.ok(d.sw <= d.w+1, name+' horizontal overflow')
   assert.ok(d.sh <= d.h+1, name+' vertical overflow '+JSON.stringify(d))
   assert.equal(await page.locator('.particle-background').count(),1)
   assert.equal(await page.locator('.cloud, .wave').count(),0)
   if (route !== 'agreement') {
    const center = await page.locator('h1').evaluate(heading => { const range=document.createRange();range.selectNodeContents(heading);const text=range.getBoundingClientRect(),area=heading.getBoundingClientRect(); return Math.abs(text.x+text.width/2-area.x-area.width/2) })
    assert.ok(center < 2, name+' heading centered')
    assert.equal(await page.locator('h1 .auth-brand').count(),1)
   }
   assert.equal(await page.locator('vite-error-overlay').count(),0)
  }
 }
 await go('agreement?document=privacy&from=login')
 await page.getByRole('heading',{ name:'隐私政策', exact:true }).waitFor()
 const indicator=page.locator('.legal-tab-indicator')
 const positionBefore = await indicator.boundingBox()
 await page.getByRole('button',{ name:'用户协议', exact:true }).click()
 await page.waitForTimeout(50)
 assert.ok(await indicator.evaluate(element => element.getAnimations().length > 0))
 await page.waitForTimeout(350)
 const positionAfter = await indicator.boundingBox()
 assert.ok(positionAfter.x < positionBefore.x)
 report.checks.push('legal tab indicator slides horizontally between documents')
 await page.getByRole('heading',{ name:'用户协议', exact:true }).waitFor()
 await page.getByRole('button',{ name:/待确认信息/ }).click()
 assert.equal(await page.getByRole('button',{ name:/待确认信息/ }).evaluate(el=>el.classList.contains('active')),true)
 assert.ok(await page.locator('.reading-progress').isVisible())
 assert.equal(await page.locator('.reading-progress').innerText(), '')
 await page.locator('.legal-artwork').evaluate(image => image.decode())
 const bar=await page.locator('.reading-progress').boundingBox(),done=await page.locator('.legal-finished').boundingBox()
 assert.ok(Math.abs(done.x-bar.x-bar.width-16) < 2)
 assert.ok(bar.width > done.width)
 report.checks.push('legal card loads new translucent Cirno scene; long progress bar has no percentage text and sits beside action')
 await page.getByRole('link',{ name:'我看完啦', exact:true }).click(); await page.waitForURL('**/login')
 report.checks.push('legal reference layout, document tabs, directory, progress and return')
 const reconnection = await page.evaluate(async () => {
  const { advanceReconnection } = await import('/src/components/particle-background/particle-network.js')
  const point = { x: 0, y: 0, reconnectTime: 3, neighbors: new Set(), excluded: new Set([1]) }
  const formerPeer = { x: 100, y: 0 }, otherPeer = { x: 210, y: 0 }
  const points = [point,formerPeer,otherPeer]
  for(let frame=0;frame<60;frame++) advanceReconnection(point,points,0.05,()=>false)
  const foundDistance=Math.hypot(point.x-otherPeer.x,point.y-otherPeer.y)
  const moved=point.x
  const blockedPoint={ x:0,y:0,reconnectTime:3,neighbors:new Set(),excluded:new Set([1]) }
  advanceReconnection(blockedPoint,[blockedPoint,formerPeer,otherPeer],0.05,()=>true)
  const staticPoint={ x:0,y:0,reconnectTime:3,neighbors:new Set(),excluded:new Set([1]) }
  advanceReconnection(staticPoint,[staticPoint,formerPeer,otherPeer],0,()=>false)
  return { moved, foundDistance, blocked:blockedPoint.x, reduced:staticPoint.x }
 })
 assert.ok(reconnection.moved > 40 && reconnection.foundDistance < 165)
 assert.equal(reconnection.blocked,0)
 assert.equal(reconnection.reduced,0)
 report.checks.push('broken particle actively seeks a different reachable peer; blocked paths and reduced-motion remain stationary')
 assert.deepEqual(report.errors,[])
 report.passed = true
} catch(e) { report.failure=e.message; throw e }
finally { await fs.writeFile(output+'/report.json',JSON.stringify(report,null,2)); await browser.close(); console.log(JSON.stringify(report,null,2)) }
