import assert from 'node:assert/strict'
import crypto from 'node:crypto'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import mysql from 'mysql2/promise'
import { createClient } from 'redis'
import nodemailer from 'nodemailer'
import amqp from 'amqplib'
import { SMTPServer } from 'smtp-server'
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const report = { checkedAt: new Date().toISOString(), passed: false, checks: [], external: {}, fixtures: 'local SMTP capture; Redis fixture verification only, no external messages', cleanupPassed: false }
const base = process.env.MENG_VERIFICATION_GATEWAY || 'http://127.0.0.1:18081', origin = 'http://127.0.0.1:5173'
const hash = value => crypto.createHmac('sha256', Buffer.from(process.env.MENG_PII_HASH_KEY, 'base64')).update(value).digest('hex')
const nonce = crypto.randomBytes(4).toString('hex'), password = 'a1' + crypto.randomBytes(9).toString('hex')
const names = ['verify_' + nonce + 'a', 'verify_' + nonce + 'b']
const contacts = names.map(name => name + '@example.invalid')
const redis = createClient({ url: 'redis://127.0.0.1:' + process.env.MENG_REDIS_PORT, password: process.env.MENG_REDIS_PASSWORD })
redis.on('error', () => {})
let db, broker, eventChannel, eventQueue
const keys = new Set(), sessions = new Set(), messages = []
const ipKey = 'meng:limit:registration-ip:' + hash('127.0.0.1')
let originalLimit, originalLimitTtl, adminCookie, resetOldPassword
const smtp = new SMTPServer({ disabledCommands: ['AUTH', 'STARTTLS'], authOptional: true, logger: false, onData(stream, session, callback) {
  const chunks = []
  stream.on('data', chunk => chunks.push(chunk))
  stream.on('end', () => {
    const raw = Buffer.concat(chunks).toString('utf8'), split = raw.indexOf('\r\n\r\n'), headers = raw.slice(0, split), body = raw.slice(split + 4)
    const text = /Content-Transfer-Encoding: base64/i.test(headers) ? Buffer.from(body.replace(/\s/g, ''), 'base64').toString('utf8') : body.replace(/=\r\n/g, '').replace(/=([0-9A-F]{2})/gi, (_, hex) => String.fromCharCode(parseInt(hex, 16)))
    messages.push({ recipient: session.envelope.rcptTo[0].address, text })
    callback()
  })
}})
async function check(name, action) { await action(); report.checks.push(name); console.log('Passed: ' + name) }
async function request(method, route, body, cookie, headers = {}) {
  const response = await fetch(base + route, { method, signal: AbortSignal.timeout(20000), headers: { Origin: origin, ...(body ? { 'Content-Type': 'application/json' } : {}), ...(cookie ? { Cookie: cookie } : {}), ...headers }, body: body ? JSON.stringify(body) : undefined })
  const data = await response.json()
  const setCookie = response.headers.get('set-cookie')
  return { status: response.status, data, cookie: setCookie?.split(';')[0], setCookie, encoding: response.headers.get('content-encoding') }
}
function expect(response, status, code) { assert.equal(response.status, status, 'unexpected HTTP status; code=' + response.data.code); if (code) assert.equal(response.data.code, code); return response }
async function fixtureTicket() { const ticket = crypto.randomUUID(), key = 'meng:captcha-ticket:' + ticket; keys.add(key); await redis.set(key, hash('127.0.0.1'), { EX: 120 }); return ticket }
function humanTrack(distance, width, height, duration = 900) { const steps = 30, stopTime = Date.now(), trackList = [{ x: 0, y: 0, t: 0, type: 'DOWN' }]; for (let step = 1; step <= steps; step++) { const progress = step / steps; trackList.push({ x: distance * (1 - (1 - progress) ** 2), y: step % 3 - 1, t: duration * progress, type: 'MOVE' }) } trackList.push({ x: distance, y: 0, t: duration, type: 'UP' }); return { bgImageWidth: width, bgImageHeight: height, startTime: stopTime - duration, stopTime, trackList } }
function trackCookie(cookie) { const claims = JSON.parse(Buffer.from(cookie.split('=')[1].split('.')[1], 'base64url').toString()); sessions.add(claims.sessionId); return claims }
async function awaitMessage(recipient, predicate = () => true) { for (let i = 0; i < 100; i++) { const message = messages.find(item => item.recipient === recipient && predicate(item.text)); if (message) return message; await new Promise(resolve => setTimeout(resolve, 100)) } throw new Error('Local SMTP capture did not receive the expected message') }
try {
  await redis.connect()
  db = await mysql.createConnection({ host: '127.0.0.1', port: Number(process.env.MENG_MYSQL_PORT), user: 'meng_user_app', password: process.env.MENG_MYSQL_USER_PASSWORD, database: 'meng_user' })
  broker = await amqp.connect({ protocol: 'amqp', hostname: '127.0.0.1', port: Number(process.env.MENG_RABBITMQ_PORT), username: process.env.MENG_RABBITMQ_USERNAME, password: process.env.MENG_RABBITMQ_PASSWORD, vhost: process.env.MENG_VERIFICATION_VHOST })
  eventChannel = await broker.createChannel()
  eventQueue = (await eventChannel.assertQueue('', { exclusive: true, autoDelete: true })).queue
  await eventChannel.bindQueue(eventQueue, 'meng.user.topic', 'user.approved')
  originalLimit = await redis.get(ipKey); originalLimitTtl = await redis.ttl(ipKey)
  await new Promise((resolve, reject) => { smtp.once('error', reject); smtp.listen(25250, '127.0.0.1', resolve) })
  await check('external QQ SMTP authentication probe (no message sent)', async () => {
    const transport = nodemailer.createTransport({ host: 'smtp.qq.com', port: 465, secure: true, auth: { user: process.env.MAIL_USERNAME, pass: process.env.MAIL_PASSWORD }, dnsTimeout: 5000, greetingTimeout: 5000, connectionTimeout: 5000, socketTimeout: 5000 })
    try { report.external.smtpAuthenticated = await transport.verify() }
    catch { report.external.smtpAuthenticated = false }
    finally { transport.close() }
  })
  await check('anonymous spoofed identity headers and internal path variants are blocked', async () => {
    expect(await request('GET', '/user/me', null, null, { 'X-User-Id': '1', 'X-User-Role': 'ADMIN' }), 401)
    for (const route of ['/user/internal/users?ids=1', '/prefix/user/internal/users', '/user/%69nternal/users', '/user/%2569nternal/users', '/user//internal;anything/users']) expect(await request('GET', route), 403)
    expect(await request('POST', '/user/sessions', { username: names[0], password, ticket: await fixtureTicket() }, null, { Origin: 'https://untrusted.invalid' }), 403)
  })
  async function challenge() {
    const generated = expect(await request('POST', '/user/captcha'), 200)
    assert.equal(generated.encoding, 'gzip', 'captcha response should be compressed by the gateway')
    const image = generated.data.data, candidates = []
    assert.equal(image.type, 'SLIDER'); assert.equal(image.backgroundImageWidth, 600); assert.equal(image.backgroundImageHeight, 360)
    for await (const batch of redis.scanIterator({ MATCH: '*'+image.id+'*', COUNT: 100 })) candidates.push(...batch)
    assert.equal(candidates.length, 1)
    const cached = JSON.parse(await redis.get(candidates[0])), percentage = Number((cached.map ?? cached).percentage)
    assert.ok(Number.isFinite(percentage), 'Captcha answer fixture schema differs')
    return { image, distance: percentage * image.backgroundImageWidth }
  }
  await check('slider-only captcha, track validation, consume-once solution, ticket replay and ticketless login rejection', async () => {
    const instant = await challenge()
    expect(await request('POST', '/user/captcha/verification', { id: instant.image.id, track: { bgImageWidth: instant.image.backgroundImageWidth, trackList: [{ x: 0, y: 0, t: 0, type: 'MOVE' }, { x: instant.distance, y: 0, t: 1000, type: 'MOVE' }] } }), 400, 'USER_INVALID_TICKET')
    const hurried = await challenge()
    expect(await request('POST', '/user/captcha/verification', { id: hurried.image.id, track: humanTrack(hurried.distance, hurried.image.backgroundImageWidth, hurried.image.backgroundImageHeight, 120) }), 400, 'USER_INVALID_TICKET')
    expect(await request('POST', '/user/sessions', { username: names[0], password }), 400, 'USER_INVALID_TICKET')
    const { image, distance } = await challenge()
    const track = humanTrack(distance, image.backgroundImageWidth, image.backgroundImageHeight)
    const verified = expect(await request('POST', '/user/captcha/verification', { id: image.id, track }), 200)
    expect(await request('POST', '/user/captcha/verification', { id: image.id, track }), 400)
    const ticket = verified.data.data.ticket
    keys.add('meng:captcha-ticket:' + ticket)
    expect(await request('POST', '/user/email-codes', { email: contacts[0], ticket }), 200)
    expect(await request('POST', '/user/email-codes', { email: contacts[1], ticket }), 400, 'USER_INVALID_TICKET')
  })
  const firstMessage = await awaitMessage(contacts[0], text => /[0-9]{6}/.test(text))
  const codeA = firstMessage.text.match(/[0-9]{6}/)[0]
  function registration(index, code, ticket) { return { username: names[index], password, confirmPassword: password, email: contacts[index], code, ticket, applyReason: 'verification fixture', termsVersion: '2026-10-09', privacyVersion: '2026-10-09' } }
  await check('MQ-backed mail code, legal versions, registration, encrypted storage and pending login', async () => {
    expect(await request('POST', '/user/registrations', { ...registration(0, codeA, await fixtureTicket()), termsVersion: 'obsolete' }), 409, 'USER_LEGAL_VERSION_CHANGED')
    expect(await request('POST', '/user/registrations', registration(0, codeA, await fixtureTicket())), 200)
    expect(await request('POST', '/user/sessions', { username: names[0], password, ticket: await fixtureTicket() }), 403, 'USER_PENDING')
    const [rows] = await db.execute('SELECT * FROM user WHERE username = ?', [names[0]])
    assert.equal(rows.length, 1); assert.ok(rows[0].email_cipher.startsWith('v1:')); assert.equal(rows[0].email_hash, hash(contacts[0])); assert.ok(!rows[0].email_cipher.includes(contacts[0])); assert.ok(rows[0].password_hash.startsWith('$2')); assert.equal(rows[0].terms_version, '2026-10-09'); assert.ok(rows[0].agreed_at); assert.equal(rows[0].level, 0); assert.equal(rows[0].delete_state, 0)
  })
  await check('administrator login, masked server pagination, mandatory rejection and immediate approval', async () => {
    const login = expect(await request('POST', '/user/sessions', { username: process.env.MENG_ADMIN_USERNAME, password: process.env.MENG_ADMIN_PASSWORD, ticket: await fixtureTicket() }), 200)
    adminCookie = login.cookie; const claims = trackCookie(adminCookie)
    assert.deepEqual(Object.keys(claims).sort(), ['sessionId', 'userId'])
    assert.match(login.setCookie, /HttpOnly/i); assert.match(login.setCookie, /SameSite=Lax/i)
    const list = expect(await request('GET', '/user/admin/registrations?page=1&size=100&status=PENDING', null, adminCookie), 200).data.data
    const row = list.records.find(item => item.username === names[0]); assert.ok(row); assert.notEqual(row.email, contacts[0])
    expect(await request('POST', '/user/admin/registrations/'+row.id+'/rejection', { reason: '' }, adminCookie), 400)
    expect(await request('POST', '/user/admin/registrations/'+row.id+'/approval', null, adminCookie), 200)
    let event
    for (let index = 0; index < 100 && !event; index++) { event = await eventChannel.get(eventQueue, { noAck: true }); if (!event) await new Promise(resolve => setTimeout(resolve, 50)) }
    assert.ok(event, 'Approval event was not published')
    const envelope = JSON.parse(event.content.toString())
    assert.deepEqual(Object.keys(envelope).sort(), ['body','eventId','eventType','occurredAt','traceId'])
    assert.equal(envelope.eventType, 'user.approved')
    assert.deepEqual(envelope.body, { userId: row.id })
    await awaitMessage(contacts[0], text => text.includes('申请已通过'))
    expect(await request('POST', '/user/admin/registrations/'+row.id+'/approval', null, adminCookie), 409)
    const loginUser = expect(await request('POST', '/user/sessions', { username: names[0], password, ticket: await fixtureTicket() }), 200); trackCookie(loginUser.cookie)
    expect(await request('GET', '/user/admin/registrations', null, loginUser.cookie, { 'X-User-Id': String(claims.userId), 'X-User-Role': 'ADMIN' }), 403)
    expect(await request('DELETE', '/user/sessions/current', null, loginUser.cookie), 200)
  })
  await check('two devices, sliding TTL, single logout and password revoke all sessions', async () => {
    const first = expect(await request('POST', '/user/sessions', { username: names[0], password, ticket: await fixtureTicket() }), 200)
    const second = expect(await request('POST', '/user/sessions', { username: names[0], password, ticket: await fixtureTicket() }), 200)
    const a = trackCookie(first.cookie), b = trackCookie(second.cookie)
    assert.notEqual(a.sessionId, b.sessionId)
    await redis.expire('meng:session:'+a.sessionId, 30)
    expect(await request('GET', '/user/me', null, first.cookie), 200)
    assert.ok(await redis.ttl('meng:session:'+a.sessionId) > 600000)
    expect(await request('DELETE', '/user/sessions/current', null, first.cookie), 200)
    expect(await request('GET', '/user/me', null, first.cookie), 401)
    expect(await request('GET', '/user/me', null, second.cookie), 200)
    const third = expect(await request('POST', '/user/sessions', { username: names[0], password, ticket: await fixtureTicket() }), 200); trackCookie(third.cookie)
    const changed = 'a1' + crypto.randomBytes(8).toString('hex')
    resetOldPassword = changed
    expect(await request('PUT', '/user/me/password', { oldPassword: password, password: changed, confirmPassword: changed }, second.cookie), 200)
    const [timestamps] = await db.execute('SELECT update_time > create_time AS advanced FROM user WHERE username = ?', [names[0]])
    assert.equal(timestamps[0].advanced, 1, 'update_time must advance after a password change')
    expect(await request('GET', '/user/me', null, second.cookie), 401)
    expect(await request('GET', '/user/me', null, third.cookie), 401)
  })
  await check('rejected account reveals reason only after valid credentials, reapply resets review', async () => {
    expect(await request('POST', '/user/email-codes', { email: contacts[1], ticket: await fixtureTicket() }), 200)
    const message = await awaitMessage(contacts[1], text => /[0-9]{6}/.test(text)), code = message.text.match(/[0-9]{6}/)[0]
    expect(await request('POST', '/user/registrations', registration(1, code, await fixtureTicket())), 200)
    const [rows] = await db.execute('SELECT id FROM user WHERE username = ?', [names[1]]), id = rows[0].id
    expect(await request('POST', '/user/admin/registrations/'+id+'/rejection', { reason: 'verification rejection' }, adminCookie), 200)
    const wrong = expect(await request('POST', '/user/sessions', { username: names[1], password: 'invalid-password', ticket: await fixtureTicket() }), 401); assert.equal(wrong.data.data, undefined)
    const rejected = expect(await request('POST', '/user/sessions', { username: names[1], password, ticket: await fixtureTicket() }), 403, 'USER_REJECTED'); assert.equal(rejected.data.data.rejectReason, 'verification rejection')
    expect(await request('POST', '/user/registrations/reapplication', { username: names[1], password, applyReason: 'verification reapplication', ticket: await fixtureTicket() }), 200)
    expect(await request('POST', '/user/registrations/reapplication', { username: names[1], password, ticket: await fixtureTicket() }), 409)
    const [updated] = await db.execute('SELECT status,reject_reason FROM user WHERE id = ?', [id]); assert.equal(updated[0].status, 'PENDING'); assert.equal(updated[0].reject_reason, null)
  })
  await check('disabled international SMS, send interval, wrong codes and numeric page validation are truthful', async () => {
    expect(await request('POST', '/user/sms-codes', { countryCode: '+886', phone: '0912345678', ticket: await fixtureTicket() }), 503, 'USER_SMS_UNAVAILABLE')
    expect(await request('POST', '/user/email-codes', { email: contacts[1], ticket: await fixtureTicket() }), 429)
    expect(await request('GET', '/user/admin/registrations?page=not-a-number', null, adminCookie), 400)
    const key = 'meng:email-code:' + hash(contacts[1]); keys.add(key)
    await redis.hSet(key, { hash: hash('123456'), errors: '0' }); await redis.expire(key, 300)
    const source = await fs.readFile(path.resolve(root, '../java_backend/meng-user/src/main/resources/redis/verify-code.lua'), 'utf8')
    for (let i = 0; i < 5; i++) assert.equal(await redis.eval(source, { keys: [key], arguments: [hash('wrong'), '5'] }), 0)
    assert.equal(await redis.exists(key), 0)
  })
  await check('password reset: isolated mail code, replay prevention, all-device revocation, and new password login', async () => {
    const accountContact = contacts[0], contactHash = hash(accountContact)
    const registrationKey = 'meng:email-code:' + contactHash
    const resetKey = 'meng:password-reset:email-code:' + contactHash
    keys.add(registrationKey); keys.add(resetKey)
    keys.add('meng:limit:password-reset:' + hash(contactHash))
    await redis.hSet(registrationKey, { hash: hash('234567'), errors: '0' }); await redis.expire(registrationKey, 300)
    const newPassword = 'reset1' + crypto.randomBytes(6).toString('hex')
    const resetBody = async code => ({ email: accountContact, code, password: newPassword, confirmPassword: newPassword, ticket: await fixtureTicket() })
    expect(await request('POST', '/user/password-resets', await resetBody('234567')), 400, 'USER_RESET_FAILED')
    assert.equal(await redis.exists(registrationKey), 1)
    const first = expect(await request('POST', '/user/sessions', { username: names[0], password: resetOldPassword, ticket: await fixtureTicket() }), 200)
    const second = expect(await request('POST', '/user/sessions', { username: names[0], password: resetOldPassword, ticket: await fixtureTicket() }), 200)
    trackCookie(first.cookie); trackCookie(second.cookie)
    // Remove only this disposable fixture's send cooldown; no shared account is modified.
    await redis.del('meng:limit:send-interval:' + hash(contactHash))
    const messageCount = messages.length
    expect(await request('POST', '/user/password-reset-codes', { email: accountContact, ticket: await fixtureTicket() }), 200)
    let resetMail
    for (let i = 0; i < 100 && !resetMail; i++) { resetMail = messages.slice(messageCount).find(item => item.recipient === accountContact && /[0-9]{6}/.test(item.text)); if (!resetMail) await new Promise(resolve => setTimeout(resolve, 100)) }
    assert.ok(resetMail, 'reset code delivered via MQ and local SMTP')
    const resetCode = resetMail.text.match(/[0-9]{6}/)[0]
    assert.equal(await redis.exists(resetKey), 1)
    expect(await request('POST', '/user/password-resets', await resetBody(resetCode)), 200)
    assert.equal(await redis.exists(resetKey), 0)
    expect(await request('GET', '/user/me', null, first.cookie), 401)
    expect(await request('GET', '/user/me', null, second.cookie), 401)
    expect(await request('POST', '/user/password-resets', await resetBody(resetCode)), 400, 'USER_RESET_FAILED')
    expect(await request('POST', '/user/sessions', { username: names[0], password: resetOldPassword, ticket: await fixtureTicket() }), 401)
    const loggedIn = expect(await request('POST', '/user/sessions', { username: names[0], password: newPassword, ticket: await fixtureTicket() }), 200)
    trackCookie(loggedIn.cookie)
    expect(await request('GET', '/user/me', null, loggedIn.cookie), 200)
    const [rows] = await db.execute('SELECT status FROM user WHERE username = ?', [names[0]])
    assert.equal(rows[0].status, 'NORMAL')
  })
  report.passed = true
} catch (error) { report.failure = error.message; process.exitCode = 1 }
finally {
  try {
    if (adminCookie) await request('DELETE', '/user/sessions/current', null, adminCookie)
    if (db) {
      const [rows] = await db.execute('SELECT id FROM user WHERE username IN (?,?)', names)
      for (const row of rows) { const members = await redis.sMembers('meng:user-sessions:'+row.id); for (const id of members) sessions.add(id); keys.add('meng:user-sessions:'+row.id) }
      await db.execute('DELETE FROM user WHERE username IN (?,?)', names)
    }
    for (const id of sessions) keys.add('meng:session:'+id)
    for (const name of names) keys.add('meng:limit:login:' + hash(name))
    for (const contact of contacts) {
      keys.add('meng:email-code:' + hash(contact))
      for (const scope of ['send-interval','send-window']) keys.add('meng:limit:'+scope+':'+hash(hash(contact)))
    }
    for (const key of keys) await redis.del(key)
    if (originalLimit != null) await redis.set(ipKey, originalLimit, originalLimitTtl > 0 ? { EX: originalLimitTtl } : {})
    else await redis.del(ipKey)
    report.cleanupPassed = true
  } catch { report.cleanupPassed = false; process.exitCode = 1 }
  if (db) await db.end()
  if (broker) await broker.close()
  if (redis.isOpen) await redis.quit()
  await new Promise(resolve => smtp.close(resolve))
  await fs.mkdir(path.join(root, 'test-output'), { recursive: true })
  await fs.writeFile(path.join(root, 'test-output', 'functional-report.json'), JSON.stringify(report, null, 2))
  console.log(JSON.stringify({ passed: report.passed, checks: report.checks.length, cleanupPassed: report.cleanupPassed, external: report.external, failure: report.failure }))
}
