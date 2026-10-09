import assert from 'node:assert/strict'
import crypto from 'node:crypto'
import fs from 'node:fs/promises'
import path from 'node:path'
import { performance } from 'node:perf_hooks'
import { fileURLToPath } from 'node:url'
import mysql from 'mysql2/promise'
import { createClient } from 'redis'
import { SMTPServer } from 'smtp-server'

// 压测只打隔离实例：18081 网关 + 18101 用户服务 + 临时 vhost + 本地收信器，结束后清掉自己造的数据
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const base = process.env.MENG_VERIFICATION_GATEWAY || 'http://127.0.0.1:18081'
const origin = 'http://127.0.0.1:5173'
const seconds = Number(process.env.MENG_LOAD_SECONDS || 8)
const warmSeconds = Number(process.env.MENG_LOAD_WARM_SECONDS || 3)
const userCount = Number(process.env.MENG_LOAD_USERS || 40)
const label = process.env.MENG_LOAD_LABEL || 'run'
// 只跑指定场景时用逗号分隔场景名，排查单个接口用
const only = process.env.MENG_LOAD_ONLY ? process.env.MENG_LOAD_ONLY.split(',') : null
const legalVersion = '2026-10-09'
const clientIp = '127.0.0.1'

const hashKey = Buffer.from(process.env.MENG_PII_HASH_KEY, 'base64')
const encryptionKey = Buffer.from(process.env.MENG_PII_ENC_KEY, 'base64')
const hash = (value) => crypto.createHmac('sha256', hashKey).update(value).digest('hex')
function encrypt(value) {
  const iv = crypto.randomBytes(12)
  const cipher = crypto.createCipheriv('aes-256-gcm', encryptionKey, iv)
  const body = Buffer.concat([cipher.update(value, 'utf8'), cipher.final(), cipher.getAuthTag()])
  return 'v1:' + Buffer.concat([iv, body]).toString('base64')
}

const nonce = crypto.randomBytes(4).toString('hex')
const password = 'a1' + crypto.randomBytes(9).toString('hex')
const seedName = `load_${nonce}_seed`
const names = Array.from({ length: userCount }, (_, index) => `load_${nonce}_${index}`)
const emailOf = (name) => `${name}@example.invalid`
const report = { label, checkedAt: new Date().toISOString(), seconds, warmSeconds, userCount, scenarios: [], passed: false, cleanupPassed: false }
const redis = createClient({ url: 'redis://127.0.0.1:' + process.env.MENG_REDIS_PORT, password: process.env.MENG_REDIS_PASSWORD })
redis.on('error', () => {})
const keys = new Set()
const sentContacts = new Set()
const messages = []
const ipLimitKey = 'meng:limit:registration-ip:' + hash(clientIp)
let db
let captchaKeyPattern
let originalLimit
let originalLimitTtl

const smtp = new SMTPServer({
  disabledCommands: ['AUTH', 'STARTTLS'],
  // 收信器默认会反查来访地址的主机名，Windows 上一次要一两秒，会把发信耗时测歪
  disableReverseLookup: true,
  authOptional: true,
  logger: false,
  onData(stream, session, callback) {
    const chunks = []
    stream.on('data', (chunk) => chunks.push(chunk))
    stream.on('end', () => {
      const raw = Buffer.concat(chunks).toString('utf8')
      const split = raw.indexOf('\r\n\r\n')
      const headers = raw.slice(0, split)
      const body = raw.slice(split + 4)
      const text = /Content-Transfer-Encoding: base64/i.test(headers)
        ? Buffer.from(body.replace(/\s/g, ''), 'base64').toString('utf8')
        : body
      messages.push({ recipient: session.envelope.rcptTo[0].address, text })
      callback()
    })
  },
})

async function request(method, route, body, cookie) {
  const response = await fetch(base + route, {
    method,
    signal: AbortSignal.timeout(30000),
    headers: {
      Origin: origin,
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(cookie ? { Cookie: cookie } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  const text = await response.text()
  return {
    status: response.status,
    bytes: text.length,
    data: text ? JSON.parse(text) : null,
    cookie: response.headers.get('set-cookie')?.split(';')[0],
  }
}

function expectOk(response, step) {
  assert.equal(response.status, 200, `${step} 返回 ${response.status} ${response.data?.code ?? ''}`)
  return response
}

async function fixtureTicket() {
  const ticket = crypto.randomUUID()
  await redis.set('meng:captcha-ticket:' + ticket, hash(clientIp), { EX: 120 })
  return ticket
}

// 轨迹时间戳由客户端上报，所以不用真的等 0.9 秒
function humanTrack(distance, width, height, duration = 900) {
  const steps = 30
  const stopTime = Date.now()
  const trackList = [{ x: 0, y: 0, t: 0, type: 'DOWN' }]
  for (let step = 1; step <= steps; step++) {
    const progress = step / steps
    trackList.push({ x: distance * (1 - (1 - progress) ** 2), y: (step % 3) - 1, t: duration * progress, type: 'MOVE' })
  }
  trackList.push({ x: distance, y: 0, t: duration, type: 'UP' })
  return { bgImageWidth: width, bgImageHeight: height, startTime: stopTime - duration, stopTime, trackList }
}

async function captchaKey(id) {
  if (!captchaKeyPattern) {
    const found = []
    for await (const batch of redis.scanIterator({ MATCH: '*' + id + '*', COUNT: 500 })) found.push(...batch)
    assert.equal(found.length, 1, '找不到滑块答案对应的键')
    captchaKeyPattern = found[0].replace(id, '{id}')
  }
  return captchaKeyPattern.replace('{id}', id)
}

async function challenge() {
  const image = expectOk(await request('POST', '/user/captcha'), '生成滑块').data.data
  const key = await captchaKey(image.id)
  keys.add(key)
  const cached = JSON.parse(await redis.get(key))
  const percentage = Number((cached.map ?? cached).percentage)
  assert.ok(Number.isFinite(percentage), '滑块答案结构变了')
  return {
    id: image.id,
    track: humanTrack(percentage * image.backgroundImageWidth, image.backgroundImageWidth, image.backgroundImageHeight),
  }
}

function percentile(sorted, ratio) {
  if (sorted.length === 0) return null
  return Number(sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * ratio) - 1)].toFixed(1))
}

// prepare 不计时，fire 计时；total 给定时按次数跑，否则按时长跑
async function scenario(name, { concurrency, total, prepare = async () => null, fire }) {
  if (only && !only.includes(name)) return null
  async function loop(duration, limit, sink) {
    let index = 0
    const stopAt = performance.now() + duration * 1000
    const started = performance.now()
    await Promise.all(
      Array.from({ length: concurrency }, async () => {
        while (limit ? index < limit : performance.now() < stopAt) {
          const current = index++
          let prepared
          try {
            prepared = await prepare(current)
          } catch (error) {
            sink.errors['prepare:' + error.message] = (sink.errors['prepare:' + error.message] || 0) + 1
            continue
          }
          const before = performance.now()
          try {
            const response = await fire(current, prepared)
            const elapsed = performance.now() - before
            if (response.status === 200) {
              sink.latencies.push(elapsed)
              sink.bytes += response.bytes ?? 0
            } else {
              const code = response.status + ':' + (response.data?.code ?? '')
              sink.errors[code] = (sink.errors[code] || 0) + 1
            }
          } catch (error) {
            sink.errors[error.name] = (sink.errors[error.name] || 0) + 1
          }
        }
      }),
    )
    return (performance.now() - started) / 1000
  }
  if (!total && warmSeconds > 0) await loop(warmSeconds, 0, { latencies: [], errors: {}, bytes: 0 })
  const sink = { latencies: [], errors: {}, bytes: 0 }
  const elapsed = await loop(seconds, total, sink)
  const sorted = sink.latencies.sort((a, b) => a - b)
  const result = {
    name,
    concurrency,
    ok: sorted.length,
    errors: sink.errors,
    perSecond: Number((sorted.length / elapsed).toFixed(1)),
    p50: percentile(sorted, 0.5),
    p95: percentile(sorted, 0.95),
    p99: percentile(sorted, 0.99),
    max: sorted.length ? Number(sorted[sorted.length - 1].toFixed(1)) : null,
    averageBytes: sorted.length ? Math.round(sink.bytes / sorted.length) : null,
  }
  report.scenarios.push(result)
  console.log(JSON.stringify(result))
  return result
}

async function awaitCode(recipient) {
  for (let attempt = 0; attempt < 150; attempt++) {
    const message = messages.find((item) => item.recipient === recipient && /[0-9]{6}/.test(item.text))
    if (message) return message.text.match(/[0-9]{6}/)[0]
    await new Promise((resolve) => setTimeout(resolve, 100))
  }
  throw new Error('本地收信器没有收到验证码')
}

// 走一遍真实注册拿到服务端算出的密码哈希，再克隆成一批压测账号
async function createUsers() {
  const seedEmail = emailOf(seedName)
  sentContacts.add(seedEmail)
  expectOk(await request('POST', '/user/email-codes', { email: seedEmail, ticket: await fixtureTicket() }), '发注册码')
  const code = await awaitCode(seedEmail)
  expectOk(
    await request('POST', '/user/registrations', {
      username: seedName,
      password,
      confirmPassword: password,
      email: seedEmail,
      code,
      ticket: await fixtureTicket(),
      applyReason: 'load fixture',
      termsVersion: legalVersion,
      privacyVersion: legalVersion,
    }),
    '注册',
  )
  const [rows] = await db.execute('SELECT password_hash FROM user WHERE username = ?', [seedName])
  assert.equal(rows.length, 1)
  for (const name of names) {
    await db.execute(
      'INSERT INTO user (username, email_cipher, email_hash, password_hash, role, status, apply_reason, level, delete_state) VALUES (?,?,?,?,?,?,?,?,?)',
      [name, encrypt(emailOf(name)), hash(emailOf(name)), rows[0].password_hash, 'USER', 'NORMAL', 'load fixture', 0, 0],
    )
  }
}

async function login(username, secret) {
  return request('POST', '/user/sessions', { username, password: secret, ticket: await fixtureTicket() })
}

try {
  await redis.connect()
  db = await mysql.createConnection({
    host: '127.0.0.1',
    port: Number(process.env.MENG_MYSQL_PORT),
    user: 'meng_user_app',
    password: process.env.MENG_MYSQL_USER_PASSWORD,
    database: 'meng_user',
  })
  originalLimit = await redis.get(ipLimitKey)
  originalLimitTtl = await redis.ttl(ipLimitKey)
  await new Promise((resolve, reject) => {
    smtp.once('error', reject)
    smtp.listen(25250, '127.0.0.1', resolve)
  })
  await createUsers()

  await scenario('生成滑块', {
    concurrency: 16,
    fire: async () => {
      const response = await request('POST', '/user/captcha')
      if (response.status === 200) keys.add(await captchaKey(response.data.data.id))
      return response
    },
  })

  await scenario('校验滑块', {
    concurrency: 16,
    prepare: () => challenge(),
    fire: (_, prepared) => request('POST', '/user/captcha/verification', prepared),
  })

  const cookies = []
  await scenario('登录', {
    concurrency: 16,
    prepare: () => fixtureTicket(),
    fire: async (index, ticket) => {
      const response = await request('POST', '/user/sessions', { username: names[index % names.length], password, ticket })
      if (response.cookie) cookies.push(response.cookie)
      return response
    },
  })
  if (!only) assert.ok(cookies.length > 0, '登录压测没有拿到任何会话')

  await scenario('当前用户', {
    concurrency: 32,
    fire: (index) => request('GET', '/user/me', null, cookies[index % cookies.length]),
  })

  const admin = expectOk(await login(process.env.MENG_ADMIN_USERNAME, process.env.MENG_ADMIN_PASSWORD), '管理员登录')
  await scenario('审核列表', {
    concurrency: 16,
    fire: () => request('GET', '/user/admin/registrations?page=1&size=20', null, admin.cookie),
  })
  await request('DELETE', '/user/sessions/current', null, admin.cookie)

  await scenario('登录全流程', {
    concurrency: 8,
    fire: async (index) => {
      const solved = await challenge()
      const verified = await request('POST', '/user/captcha/verification', solved)
      if (verified.status !== 200) return verified
      const session = await request('POST', '/user/sessions', {
        username: names[index % names.length],
        password,
        ticket: verified.data.data.ticket,
      })
      if (session.status !== 200) return session
      const me = await request('GET', '/user/me', null, session.cookie)
      if (me.status !== 200) return me
      return request('DELETE', '/user/sessions/current', null, session.cookie)
    },
  })

  await scenario('发邮箱验证码', {
    concurrency: 8,
    total: 40,
    prepare: () => fixtureTicket(),
    fire: (index, ticket) => {
      const email = `load_${nonce}_mail_${index}@example.invalid`
      sentContacts.add(email)
      return request('POST', '/user/email-codes', { email, ticket })
    },
  })

  report.passed = report.scenarios.length > 0 && report.scenarios.every((item) => item.ok > 0 && Object.keys(item.errors).length === 0)
  if (!report.passed) process.exitCode = 1
} catch (error) {
  report.failure = error.message
  process.exitCode = 1
} finally {
  try {
    if (db) {
      const created = [seedName, ...names]
      const marks = created.map(() => '?').join(',')
      const [rows] = await db.execute(`SELECT id FROM user WHERE username IN (${marks})`, created)
      for (const row of rows) {
        for (const id of await redis.sMembers('meng:user-sessions:' + row.id)) keys.add('meng:session:' + id)
        keys.add('meng:user-sessions:' + row.id)
      }
      await db.execute(`DELETE FROM user WHERE username IN (${marks})`, created)
      for (const name of created) keys.add('meng:limit:login:' + hash(name))
    }
    for (const contact of sentContacts) {
      keys.add('meng:email-code:' + hash(contact))
      for (const scope of ['send-interval', 'send-window']) keys.add('meng:limit:' + scope + ':' + hash(hash(contact)))
    }
    const pending = [...keys]
    for (let start = 0; start < pending.length; start += 500) await redis.del(pending.slice(start, start + 500))
    if (originalLimit != null) await redis.set(ipLimitKey, originalLimit, originalLimitTtl > 0 ? { EX: originalLimitTtl } : {})
    else await redis.del(ipLimitKey)
    report.cleanupPassed = true
  } catch (error) {
    report.cleanupPassed = false
    report.cleanupFailure = error.message
    process.exitCode = 1
  }
  if (db) await db.end()
  if (redis.isOpen) await redis.quit()
  await new Promise((resolve) => smtp.close(resolve))
  await fs.mkdir(path.join(root, 'test-output'), { recursive: true })
  await fs.writeFile(path.join(root, 'test-output', `load-${label}.json`), JSON.stringify(report, null, 2))
  console.log(JSON.stringify({ passed: report.passed, cleanupPassed: report.cleanupPassed, failure: report.failure }))
}
