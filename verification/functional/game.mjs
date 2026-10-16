import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import mysql from 'mysql2/promise';
import { createClient } from 'redis';
// 萌岛：自动建档、拾取的距离和周期去重、购买扣款与并发、换装、神殿只奖励一次、存档范围、金币流水对账，以及金币不碰成长域；全程走网关，结束后清理自己造的数据
const origin = 'http://127.0.0.1:5173', base = 'http://127.0.0.1:8080', nonce = crypto.randomBytes(3).toString('hex');
const world = JSON.parse(readFileSync(new URL('../../java_backend/meng-game/src/main/resources/game/world.json', import.meta.url)));
const users = [], cookies = [], keys = [], checks = [];
const connect = (domain) => mysql.createConnection({ host: '127.0.0.1', port: Number(process.env.MENG_MYSQL_PORT), user: `meng_${domain}_app`, password: process.env[`MENG_MYSQL_${domain.toUpperCase()}_PASSWORD`], database: `meng_${domain}` });
const redis = createClient({ url: `redis://127.0.0.1:${process.env.MENG_REDIS_PORT}`, password: process.env.MENG_REDIS_PASSWORD });
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
let userDb, gameDb, growthDb;
async function api(method, url, body, index = 0) {
  const response = await fetch(base + url, { method, headers: { Origin: origin, Cookie: cookies[index], ...(body ? { 'Content-Type': 'application/json' } : {}) }, body: body ? JSON.stringify(body) : undefined });
  return { status: response.status, ...(await response.json()) };
}
function ok(result, status = 200) { assert.equal(result.status, status, JSON.stringify(result)); return result.data; }
function rejected(result, status, code) { assert.equal(result.status, status, JSON.stringify(result)); assert.equal(result.code, code); }
async function check(name, run) { await run(); checks.push(name); console.log('PASS ' + name); }
async function eventually(read, label, attempts = 60) { for (let i = 0; i < attempts; i++) { const value = await read(); if (value) return value; await wait(150); } throw Error('Timed out: ' + label); }
const spot = (id) => world.spots.find((s) => s.id === id);
const firstCoin = world.spots.find((s) => s.kind === 'COIN').id;
const firstChest = world.spots.find((s) => s.kind === 'CHEST').id;
const take = (id, index = 0) => { const s = spot(id); return api('POST', '/game/pickups', { spotId: id, x: s.x + 1, z: s.z }, index); };
try {
  await eventually(async () => (await fetch('http://127.0.0.1:8105/actuator/health').catch(() => null))?.status === 200, 'game service', 200);
  await redis.connect();
  userDb = await connect('user'); gameDb = await connect('game'); growthDb = await connect('growth');
  const [[admin]] = await userDb.execute('SELECT password_hash FROM user WHERE username=?', [process.env.MENG_ADMIN_USERNAME]);
  for (let i = 0; i < 2; i++) {
    const username = 'verify_game_' + nonce + '_' + i;
    const [insert] = await userDb.execute("INSERT INTO user(username,password_hash,role,status,level,nickname) VALUES(?,?,'USER','NORMAL',1,?)", [username, admin.password_hash, '岛' + i + nonce]);
    users.push(insert.insertId);
    const ticket = crypto.randomUUID(), key = 'meng:captcha-ticket:' + ticket; keys.push(key);
    await redis.set(key, crypto.createHmac('sha256', Buffer.from(process.env.MENG_PII_HASH_KEY, 'base64')).update('127.0.0.1').digest('hex'), { EX: 120 });
    const response = await fetch(base + '/user/sessions', { method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password: process.env.MENG_ADMIN_PASSWORD, ticket }) });
    assert.equal(response.status, 200);
    cookies.push(response.headers.get('set-cookie').split(';')[0]);
  }
  await check('Game endpoints require login', async () => {
    const response = await fetch(base + '/game/profile', { headers: { Origin: origin } });
    assert.equal(response.status, 401);
  });
  await check('First visit creates a profile at the beach spawn with no coins', async () => {
    const me = ok(await api('GET', '/game/profile'));
    assert.equal(me.coins, 0); assert.equal(me.stars, 0); assert.equal(me.cleared, false);
    assert.equal(me.x, world.spawn.x); assert.equal(me.z, world.spawn.z);
    const map = ok(await api('GET', '/game/world'));
    assert.equal(map.spots.length, world.spots.length); assert.ok(map.spots.every((s) => !s.taken));
    assert.equal(map.items.length, 8); assert.ok(map.items.every((i) => !i.owned));
    assert.deepEqual(map.shrine, world.shrine);
  });
  await check('A coin is taken once per day, far or unknown pickups are rejected', async () => {
    const first = ok(await take(firstCoin));
    assert.equal(first.amount, spot(firstCoin).amount); assert.equal(first.taken, false); assert.equal(first.coins, spot(firstCoin).amount);
    const again = ok(await take(firstCoin));
    assert.equal(again.amount, 0); assert.equal(again.taken, true); assert.equal(again.coins, first.coins);
    rejected(await api('POST', '/game/pickups', { spotId: world.spots.filter((s) => s.kind === 'COIN')[1].id, x: 0, z: 0 }), 400, 'GAME_TOO_FAR');
    rejected(await api('POST', '/game/pickups', { spotId: 'nope', x: 0, z: 0 }), 404, 'COMMON_NOT_FOUND');
    ok(await api('POST', '/game/pickups', { spotId: '' }), 400);
    assert.ok(ok(await api('GET', '/game/world')).spots.find((s) => s.id === firstCoin).taken);
    assert.equal(ok(await api('GET', '/game/profile', null, 1)).coins, 0);
  });
  await check('Buying needs enough coins; spending and ownership land together', async () => {
    const before = ok(await api('GET', '/game/profile')).coins;
    rejected(await api('POST', '/game/shop/purchases', { itemId: 'hat-crown' }), 409, 'GAME_NOT_ENOUGH_COINS');
    assert.equal(ok(await api('GET', '/game/profile')).coins, before);
    assert.ok(!ok(await api('GET', '/game/world')).items.find((i) => i.id === 'hat-crown').owned);
    const chests = world.spots.filter((x) => x.kind === 'CHEST');
    for (const s of chests) assert.equal(ok(await take(s.id)).amount, 20);
    const bought = ok(await api('POST', '/game/shop/purchases', { itemId: 'hat-crown' }));
    assert.equal(bought.amount, -80); assert.equal(bought.coins, before + chests.length * 20 - 80);
    rejected(await api('POST', '/game/shop/purchases', { itemId: 'hat-crown' }), 409, 'GAME_ITEM_OWNED');
    rejected(await api('POST', '/game/shop/purchases', { itemId: 'nope' }), 404, 'COMMON_NOT_FOUND');
  });
  await check('Concurrent purchases never double-sell or overdraw', async () => {
    const same = await Promise.all([1, 2, 3].map(() => api('POST', '/game/shop/purchases', { itemId: 'outfit-mint' })));
    assert.equal(same.filter((r) => r.status === 200).length, 1);
    assert.ok(same.filter((r) => r.status !== 200).every((r) => r.code === 'GAME_ITEM_OWNED'), JSON.stringify(same));
    assert.deepEqual(ok(await api('GET', '/game/world')).items.filter((i) => i.owned).map((i) => i.id).sort(), ['hat-crown', 'outfit-mint']);
    // 第二个账号只有一个宝箱的 20 金币，同时买两件 20 的，只能成一件
    assert.equal(ok(await take(firstChest, 1)).coins, 20);
    const race = await Promise.all(['outfit-pink', 'outfit-sky'].map((itemId) => api('POST', '/game/shop/purchases', { itemId }, 1)));
    assert.equal(race.filter((r) => r.status === 200).length, 1);
    assert.equal(race.find((r) => r.status !== 200).code, 'GAME_NOT_ENOUGH_COINS');
    assert.equal(ok(await api('GET', '/game/profile', null, 1)).coins, 0);
  });
  await check('Only owned items of the right slot can be worn', async () => {
    rejected(await api('PUT', '/game/equipment', { hat: 'hat-bow', outfit: null }), 409, 'GAME_ITEM_NOT_OWNED');
    rejected(await api('PUT', '/game/equipment', { hat: 'outfit-mint', outfit: null }), 409, 'GAME_ITEM_NOT_OWNED');
    const worn = ok(await api('PUT', '/game/equipment', { hat: 'hat-crown', outfit: 'outfit-mint' }));
    assert.equal(worn.hat, 'hat-crown'); assert.equal(worn.outfit, 'outfit-mint');
    assert.equal(ok(await api('PUT', '/game/equipment', { hat: null, outfit: 'outfit-mint' })).hat, undefined);
  });
  await check('The shrine needs every star and rewards only the first time', async () => {
    rejected(await api('POST', '/game/shrine'), 409, 'GAME_STARS_MISSING');
    const stars = world.spots.filter((s) => s.kind === 'STAR');
    for (const [i, s] of stars.entries()) { const r = ok(await take(s.id)); assert.equal(r.amount, 0); assert.equal(r.stars, i + 1); }
    const before = ok(await api('GET', '/game/profile')).coins;
    const first = ok(await api('POST', '/game/shrine'));
    assert.equal(first.amount, 100); assert.equal(first.coins, before + 100);
    const second = ok(await api('POST', '/game/shrine'));
    assert.equal(second.amount, 0); assert.equal(second.coins, before + 100);
    const me = ok(await api('GET', '/game/profile')); assert.equal(me.cleared, true); assert.equal(me.stars, 3);
  });
  await check('Position saves inside the island and comes back on the next visit', async () => {
    ok(await api('PUT', '/game/save', { x: -40.5, y: 3.2, z: 2.25, yaw: 1.5 }));
    const me = ok(await api('GET', '/game/profile'));
    assert.deepEqual([me.x, me.y, me.z, me.yaw], [-40.5, 3.2, 2.25, 1.5]);
    rejected(await api('PUT', '/game/save', { x: 600, y: 1, z: 0, yaw: 0 }), 400, 'GAME_OUT_OF_ISLAND');
    rejected(await api('PUT', '/game/save', { x: 0, y: 400, z: 0, yaw: 0 }), 400, 'GAME_OUT_OF_ISLAND');
    ok(await api('PUT', '/game/save', { x: 0, z: 0 }), 400);
  });
  await check('The coin ledger reconciles with the balance and growth points stay untouched', async () => {
    const [rows] = await gameDb.query('SELECT kind, amount, balance_after FROM game_coin_record WHERE user_id=? ORDER BY id', [users[0]]);
    const [[player]] = await gameDb.query('SELECT coins FROM game_player WHERE user_id=?', [users[0]]);
    assert.equal(rows.reduce((sum, r) => sum + r.amount, 0), Number(player.coins));
    assert.equal(Number(rows.at(-1).balance_after), Number(player.coins));
    assert.deepEqual([...new Set(rows.map((r) => r.kind))].sort(), ['CLEAR', 'PICKUP', 'PURCHASE']);
    const [points] = await growthDb.query('SELECT COUNT(*) AS n FROM point_record WHERE user_id IN (?)', [users]);
    assert.equal(points[0].n, 0);
  });
  console.log(`Game verification passed: ${checks.length} checks.`);
} finally {
  if (gameDb && users.length) for (const table of ['game_coin_record', 'game_item', 'game_pickup', 'game_player']) await gameDb.query(`DELETE FROM ${table} WHERE user_id IN (?)`, [users]);
  await gameDb?.end();
  if (growthDb && users.length) for (const table of ['exp_record', 'check_in', 'growth_account', 'point_record', 'point_account']) await growthDb.query(`DELETE FROM ${table} WHERE user_id IN (?)`, [users]);
  await growthDb?.end();
  if (redis.isOpen) {
    for (const cookie of cookies) { const claims = JSON.parse(Buffer.from(cookie.split('=')[1].split('.')[1], 'base64url')); await redis.del('meng:session:' + claims.sessionId); }
    for (const user of users) await redis.del('meng:user-sessions:' + user);
    for (const key of keys) await redis.del(key);
    await redis.quit();
  }
  if (userDb && users.length) await userDb.query('DELETE FROM user WHERE id IN (?)', [users]);
  await userDb?.end();
}
