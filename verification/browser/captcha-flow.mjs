import { chromium } from 'playwright'
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { captchaAnswer, dragKnob, knobDistance, solveCaptcha } from './captcha.mjs'

// 滑块验证专项：用户端登录每次都先过滑块；拖错时停在原位、提示并抖动，稍后换图；票据随登录请求提交。
// 登录接口用模拟响应，不产生真实的登录失败计数；滑块生成与校验走真实服务。
const output = fileURLToPath(new URL('../test-output/captcha/', import.meta.url))
await fs.mkdir(output, { recursive: true })
const browser = await chromium.launch({ channel: 'msedge', headless: true })
const report = { checkedAt: new Date().toISOString(), passed: false, checks: [], failures: [] }
async function check(name, action) {
  await action()
  report.checks.push(name)
}
const pieceLeft = (page) =>
  page.locator('.captcha-board .piece').evaluate((piece) => piece.getBoundingClientRect().left - piece.parentElement.getBoundingClientRect().left)
try {
  const page = await browser.newPage({ viewport: { width: 1366, height: 768 } })
  page.on('pageerror', (error) => report.failures.push(error.message))
  // 记录每次滑块校验的结果，失败时便于判断是没拖上还是被拒
  report.verifications = []
  page.on('response', (response) => {
    if (response.url().endsWith('/user/captcha/verification')) report.verifications.push(response.status())
  })
  const logins = []
  await page.route('**/user/sessions', (route) => {
    logins.push(route.request().postDataJSON())
    return route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({ code: 'USER_INVALID_CREDENTIALS', message: 'verification mocked failure' }),
    })
  })
  await page.goto('http://127.0.0.1:5173/login')
  await page.locator('h1').waitFor()
  await page.locator('input[autocomplete="username"]').fill('captcha_flow_probe')
  await page.locator('input[autocomplete="current-password"]').fill('not-a-real-password-1')
  await page.getByRole('checkbox').check()
  const login = page.getByRole('button', { name: '登录', exact: true })
  const dialog = page.getByRole('dialog')
  let first

  await check('login opens the slider dialog before any login request', async () => {
    const challenge = page.waitForResponse((response) => response.url().endsWith('/user/captcha'))
    await login.click()
    await dialog.waitFor()
    first = (await (await challenge).json()).data
    assert.equal(first.type, 'SLIDER')
    assert.equal(first.backgroundImageWidth, 600)
    assert.equal(first.backgroundImageHeight, 360)
    await page.locator('.captcha-board .background').evaluate((element) => element.decode())
    await page.locator('.captcha-board .piece').evaluate((element) => element.decode())
    await page.waitForTimeout(500)
    await page.screenshot({ path: path.join(output, 'login-dialog.png') })
    assert.equal(logins.length, 0)
  })

  await check('title is bold and centred; thin rail with the refresh icon on the same row; no hint text', async () => {
    const title = dialog.locator('.el-dialog__title')
    const box = await dialog.boundingBox()
    const titleBox = await title.boundingBox()
    assert.ok(Math.abs(titleBox.x + titleBox.width / 2 - (box.x + box.width / 2)) <= 2, 'title is not centred')
    assert.ok(Number(await title.evaluate((element) => getComputedStyle(element).fontWeight)) >= 700)
    const rail = await page.locator('.slider-rail').boundingBox()
    const refresh = await dialog.getByRole('button', { name: '换一张', exact: true }).boundingBox()
    assert.ok(refresh.x >= rail.x + rail.width, 'refresh icon is not to the right of the rail')
    assert.ok(Math.abs(refresh.y + refresh.height / 2 - (rail.y + rail.height / 2)) <= 2, 'refresh icon is not on the rail row')
    assert.equal(await dialog.getByRole('button', { name: '换一张', exact: true }).innerText(), '')
    const track = await page.locator('.slider-rail').evaluate((element) => parseFloat(getComputedStyle(element, '::before').height))
    assert.ok(track <= 14, 'rail is not a thin track')
    assert.equal(await dialog.getByText('拖动滑块，让拼图回到正确的位置').count(), 0)
    assert.equal(await page.locator('.captcha-board .veil').count(), 1)
  })

  await check('a wrong drag stays in place, shakes and explains, then a new picture slides in', async () => {
    const answer = await captchaAnswer(first.id)
    const wrong = answer > 0.45 ? answer - 0.3 : answer + 0.3
    const refused = page.waitForResponse((response) => response.url().endsWith('/user/captcha/verification'))
    const reloaded = page.waitForResponse((response) => response.url().endsWith('/user/captcha'))
    await dragKnob(page, await knobDistance(page, wrong), false)
    const dragged = await pieceLeft(page)
    const trail = await page.locator('.captcha-board .trail').evaluate((element) => ({ opacity: Number(getComputedStyle(element).opacity), width: element.getBoundingClientRect().width }))
    assert.ok(trail.opacity > 0.9 && trail.width > dragged, 'slid area is not highlighted')
    await page.screenshot({ path: path.join(output, 'login-dialog-dragging.png') })
    await page.mouse.up()
    assert.equal((await refused).status(), 400)
    await dialog.getByRole('alert').filter({ hasText: '验证失败，请再试一次' }).waitFor()
    assert.ok(Math.abs((await pieceLeft(page)) - dragged) <= 12, 'piece jumped back immediately')
    assert.equal(await page.locator('.captcha-board.is-shaking').count(), 1)
    const board = await page.locator('.captcha-board').boundingBox()
    const messageBox = await dialog.getByRole('alert').boundingBox()
    assert.ok(messageBox.y >= board.y + board.height - 12, 'message is not below the picture')
    await page.screenshot({ path: path.join(output, 'login-dialog-failed.png') })
    assert.equal((await reloaded).status(), 200)
    await page.waitForFunction(() => {
      const piece = document.querySelector('.captcha-board .piece')
      return piece && piece.getBoundingClientRect().left - piece.parentElement.getBoundingClientRect().left < 1
    })
    assert.equal(await dialog.isVisible(), true)
    assert.equal(logins.length, 0)
    await page.locator('.el-dialog__headerbtn').click()
    await dialog.waitFor({ state: 'hidden' })
    assert.equal(logins.length, 0)
  })

  await check('solving the slider sends the ticket with the login request', async () => {
    await solveCaptcha(page, () => login.click())
    await page.getByRole('alert').filter({ hasText: '用户名或密码错误' }).waitFor()
    assert.equal(logins.length, 1)
    assert.match(logins[0].ticket, /^[0-9a-f-]{36}$/)
  })

  await check('the next login attempt asks for the slider again with a fresh ticket', async () => {
    await solveCaptcha(page, () => login.click())
    await page.getByRole('alert').filter({ hasText: '用户名或密码错误' }).waitFor()
    await page.waitForFunction(() => !document.querySelector('.el-button.is-loading'))
    assert.equal(logins.length, 2)
    assert.notEqual(logins[1].ticket, logins[0].ticket)
  })

  await check('the refresh icon loads another picture', async () => {
    const opened = page.waitForResponse((response) => response.url().endsWith('/user/captcha'))
    await login.click()
    await dialog.waitFor()
    const before = (await (await opened).json()).data.id
    const reloaded = page.waitForResponse((response) => response.url().endsWith('/user/captcha'))
    await dialog.getByRole('button', { name: '换一张', exact: true }).click()
    assert.notEqual((await (await reloaded).json()).data.id, before)
    await page.locator('.el-dialog__headerbtn').click()
    await dialog.waitFor({ state: 'hidden' })
  })

  assert.deepEqual(report.failures, [])
  report.passed = true
} catch (error) {
  report.failure = String(error.message ?? error)
  process.exitCode = 1
} finally {
  await browser.close()
  await fs.writeFile(path.join(output, 'report.json'), JSON.stringify(report, null, 2))
  console.log(JSON.stringify({ passed: report.passed, checks: report.checks.length, failure: report.failure }))
}
