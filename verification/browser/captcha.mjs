import { createClient } from 'redis'
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'

// 验证脚本共用的滑块求解。答案只在这里从验证码库的 Redis 缓存读取，产品页面和接口从不暴露它。

async function redisPort() {
  if (process.env.MENG_REDIS_PORT) return process.env.MENG_REDIS_PORT
  const ports = await fs.readFile(new URL('../../deploy/ports.env', import.meta.url), 'utf8')
  const match = ports.match(/^MENG_REDIS_PORT=(\d+)\s*$/m)
  assert.ok(match, 'MENG_REDIS_PORT is not configured')
  return match[1]
}

// 缺口左边缘占背景宽度的比例
export async function captchaAnswer(id) {
  assert.ok(process.env.MENG_REDIS_PASSWORD, 'MENG_REDIS_PASSWORD is not available to the verification process')
  const redis = createClient({
    url: 'redis://127.0.0.1:' + (await redisPort()),
    password: process.env.MENG_REDIS_PASSWORD,
  })
  await redis.connect()
  try {
    let answer
    for await (const batch of redis.scanIterator({ MATCH: '*' + id + '*', COUNT: 100 })) {
      for (const key of batch) {
        const cached = JSON.parse(await redis.get(key))
        answer = Number((cached.map ?? cached).percentage)
      }
    }
    assert.ok(Number.isFinite(answer), 'Captcha answer fixture schema differs')
    return answer
  } finally {
    await redis.quit()
  }
}

// 把「拼图左边缘到达背景宽度的 percentage 处」换算成滑块要移动的像素。
// 滑块走满轨道时拼图正好贴到右边，两者行程不同。
export async function knobDistance(page, percentage) {
  // 弹窗弹出时带缩放动画，等画布的可见宽度回到布局宽度再量，否则量到的是缩小后的尺寸
  await page.waitForFunction(() => {
    const board = document.querySelector('.captcha-board')
    return board && Math.abs(board.getBoundingClientRect().width - board.clientWidth) < 0.5
  })
  const board = await page.locator('.captcha-board').boundingBox()
  const piece = await page.locator('.captcha-board .piece').boundingBox()
  const rail = await page.locator('.slider-rail').boundingBox()
  const knob = await page.locator('.slider-knob').boundingBox()
  return ((percentage * board.width) / (board.width - piece.width)) * (rail.width - knob.width)
}

// 按住滑块拖动：先快后慢、带轻微上下抖动，总时长超过轨迹检测的下限。release 为 false 时停在终点不松手。
export async function dragKnob(page, distance, release = true) {
  const knob = await page.locator('.slider-knob').boundingBox()
  const startX = knob.x + knob.width / 2
  const startY = knob.y + knob.height / 2
  const steps = 24
  await page.mouse.move(startX, startY)
  await page.mouse.down()
  for (let step = 1; step <= steps; step++) {
    const progress = step / steps
    await page.mouse.move(startX + distance * (1 - (1 - progress) ** 2), startY + (step % 3) - 1)
    await page.waitForTimeout(25)
  }
  if (release) await page.mouse.up()
}

// trigger 是会弹出滑块弹窗的那次点击
export async function solveCaptcha(page, trigger, screenshot) {
  const imageResponse = page.waitForResponse(
    (response) => response.url().endsWith('/user/captcha') && response.status() === 200,
  )
  await trigger()
  await page.getByRole('dialog').waitFor()
  await page.locator('.captcha-board .background').evaluate((image) => image.decode())
  await page.locator('.captcha-board .piece').evaluate((image) => image.decode())
  if (screenshot) await page.screenshot({ path: screenshot, fullPage: true })
  const image = (await (await imageResponse).json()).data
  assert.equal(image.type, 'SLIDER')
  assert.equal(image.backgroundImageWidth, 600)
  assert.equal(image.backgroundImageHeight, 360)
  await dragKnob(page, await knobDistance(page, await captchaAnswer(image.id)))
  await page.getByRole('dialog').waitFor({ state: 'hidden' })
  return image
}
