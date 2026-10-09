import { chromium } from 'playwright'
import { solveCaptcha } from './captcha.mjs'
import { releaseSmsFixture } from './limits.mjs'
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const output = path.join(root, 'test-output', 'browser')
await fs.mkdir(output, { recursive: true })
const browser = await chromium.launch({ channel: 'msedge', headless: true })
const report = { checkedAt: new Date().toISOString(), passed: false, browser: 'Microsoft Edge / Playwright', fallback: 'Browser plugin not available', checks: [], failures: [] }
async function check(name, action) { await action(); report.checks.push(name) }
try {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  const page = await context.newPage()
  page.on('pageerror', error => report.failures.push(error.name))
  await check('reference screenshots captured', async () => {
    await page.goto('http://127.0.0.1:5180/' + encodeURIComponent('萌部落 · 登录页-琪露诺预览.html'))
    await page.locator('#__bundler_loading').waitFor({ state: 'hidden', timeout: 30000 })
    await page.screenshot({ path: path.join(output, 'reference-login.png'), fullPage: true })
    await page.goto('http://127.0.0.1:5180/Register-cirno-preview.dc.html')
    await page.waitForTimeout(1500)
    await page.screenshot({ path: path.join(output, 'reference-register.png'), fullPage: true })
  })
  await check('user login and local poster/font assets render', async () => {
    await page.goto('http://127.0.0.1:5173/login')
    await page.getByRole('heading', { name: /登录.*萌部落/ }).waitFor()
    await page.locator('.poster > img').evaluate(image => image.decode())
    await page.evaluate(() => document.fonts.ready)
    assert.equal(await page.evaluate(async () => { await document.fonts.load('44px "ZCOOL KuaiLe"', '欢迎回来呀'); return document.fonts.check('44px "ZCOOL KuaiLe"', '欢迎回来呀') }), true)
    await page.screenshot({ path: path.join(output, 'login-desktop.png'), fullPage: true })
  })
  await check('default Taiwan region and email/mobile switch', async () => {
    await page.getByRole('link', { name: '申请加入' }).click()
    await page.getByRole('heading', { name: /加入\s*萌部落/ }).waitFor()
    assert.match(await page.locator('.contact-region').innerText(), /台湾.*\+886/)
    await page.getByRole('button', { name: '切换为邮箱', exact: true }).click()
    assert.equal(await page.getByPlaceholder('请输入邮箱地址').count(), 1)
    await page.getByRole('button', { name: '切换为手机号', exact: true }).click()
    await page.locator('.contact-region').click()
    await page.getByRole('option', { name: '日本 +81' }).click()
    await page.screenshot({ path: path.join(output, 'register-desktop.png'), fullPage: true })
  })
  await check('registration validation and real captcha images', async () => {
    await page.getByRole('button', { name: '提交申请', exact: true }).click()
    await page.getByRole('alert').filter({ hasText: '请输入用户名' }).waitFor()
    await page.getByPlaceholder('请输入当地手机号').fill('09012345678')
    await solveCaptcha(page, () => page.getByRole('button', { name: '发送验证码', exact: true }).click(), path.join(output, 'captcha-desktop.png'))
    await page.getByRole('alert').filter({ hasText: '短信通道尚未配置' }).first().waitFor()
  })
  await check('agreement page and missing formal text are explicit', async () => {
    await page.goto('http://127.0.0.1:5173' + await page.getByRole('link', { name: '用户协议', exact: true }).getAttribute('href'))
    await page.getByRole('heading', { name: '正式条款待发布' }).waitFor()
    await page.getByRole('button', { name: '隐私政策', exact: true }).click()
    await page.getByRole('heading', { name: '正式政策待发布' }).waitFor()
    assert.equal(await page.evaluate(() => document.documentElement.scrollHeight <= innerHeight + 1), true)
    await page.screenshot({ path: path.join(output, 'agreement-desktop.png'), fullPage: true })
  })
  await check('unauthenticated protected route redirects to login', async () => {
    await page.goto('http://127.0.0.1:5173/account')
    await page.waitForURL(url => url.pathname === '/login' && url.searchParams.get('redirect') === '/account')
  })
  await check('admin login and server-backed pagination', async () => {
    await page.goto('http://127.0.0.1:5174/login')
    await page.getByRole('heading', { name: '管理后台', exact: true }).waitFor()
    await page.locator('input[autocomplete="username"]').fill(process.env.MENG_ADMIN_USERNAME)
    await page.locator('input[autocomplete="current-password"]').fill(process.env.MENG_ADMIN_PASSWORD)
    await solveCaptcha(page, () => page.getByRole('button', { name: '登录', exact: true }).click(), path.join(output, 'captcha-admin-login.png'))
    await page.getByRole('heading', { name: '注册申请', exact: true }).waitFor()
    await page.screenshot({ path: path.join(output, 'admin-desktop.png'), fullPage: true })
    await page.getByRole('button', { name: '退出登录', exact: true }).click()
    await page.getByRole('heading', { name: '管理后台', exact: true }).waitFor()
  })
  const mobile = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, reducedMotion: 'reduce' })
  const phone = await mobile.newPage()
  await check('mobile login/register/agreement have no horizontal overflow', async () => {
    for (const route of ['login', 'register', 'agreement']) {
      await phone.goto('http://127.0.0.1:5173/' + route)
      await phone.locator('h1').waitFor()
      await phone.evaluate(() => document.fonts.ready)
      assert.equal(await phone.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, route + ' overflow')
      await phone.screenshot({ path: path.join(output, route + '-mobile.png'), fullPage: true })
    }
  })
  assert.deepEqual(report.failures, [])
  report.passed = true
} catch (error) {
  report.failure = error.message.replaceAll(process.env.MENG_ADMIN_PASSWORD || 'no-secret-match', '[redacted]')
  process.exitCode = 1
} finally {
  // 注册页那一步用日本号码 090-1234-5678 请求过一次短信码，把它记下的限流计数还回去
  try { await releaseSmsFixture('+819012345678') } catch (error) { report.cleanupFailure = error.message; process.exitCode = 1 }
  await browser.close()
  await fs.writeFile(path.join(root, 'test-output', 'browser-report.json'), JSON.stringify(report, null, 2))
  console.log(JSON.stringify({ passed: report.passed, checks: report.checks.length, failure: report.failure }))
}
