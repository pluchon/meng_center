import crypto from 'node:crypto'
import assert from 'node:assert/strict'
import { createClient } from 'redis'
import { redisPort } from './captcha.mjs'

// 浏览器回归会真的请求一次发短信码（通道没开通，返回 503），服务端照样记了限流。
// 这里把这一次记下的计数还回去：同一天反复跑回归，不会把本机的短信日上限（按来源地址 20 次）用光
export async function releaseSmsFixture(normalizedPhone, ip = '127.0.0.1') {
  assert.ok(process.env.MENG_PII_HASH_KEY, 'MENG_PII_HASH_KEY is not available to the verification process')
  const hashKey = Buffer.from(process.env.MENG_PII_HASH_KEY, 'base64')
  const hash = (value) => crypto.createHmac('sha256', hashKey).update(value).digest('hex')
  const redis = createClient({
    url: 'redis://127.0.0.1:' + (await redisPort()),
    password: process.env.MENG_REDIS_PASSWORD,
  })
  await redis.connect()
  try {
    // 发送间隔和发送窗口按联系方式记，这个号码只有验证脚本在用，直接删
    const contact = hash(hash(normalizedPhone))
    await redis.del(['meng:limit:send-interval:' + contact, 'meng:limit:send-window:' + contact])
    // 来源地址的日计数和本机其他操作共用，只退回这一次
    const daily = 'meng:limit:sms-ip:' + hash(ip)
    if (Number(await redis.get(daily)) > 0) await redis.decr(daily)
  } finally {
    await redis.quit()
  }
}
