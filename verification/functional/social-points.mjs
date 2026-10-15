import { cleanupImFixtures } from './im-fixture-cleanup.mjs';
import { cleanupPostReadModels } from './read-model-cleanup.mjs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { createClient } from 'redis';
// 关注与粉丝、关注通知、收藏夹公开私密、别人主页的内容接口，以及积分的获得与明细；全程走网关，结束后清理自己造的数据
const origin = 'http://127.0.0.1:5173', base = 'http://127.0.0.1:8080', nonce = crypto.randomBytes(3).toString('hex');
const users = [], cookies = [], keys = [], checks = [];
const connect = (domain) => mysql.createConnection({ host: '127.0.0.1', port: Number(process.env.MENG_MYSQL_PORT), user: `meng_${domain}_app`, password: process.env[`MENG_MYSQL_${domain.toUpperCase()}_PASSWORD`], database: `meng_${domain}` });
const redis = createClient({ url: `redis://127.0.0.1:${process.env.MENG_REDIS_PORT}`, password: process.env.MENG_REDIS_PASSWORD });
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
let userDb, contentDb, growthDb;
async function api(method, url, body, index = 0) {
  const response = await fetch(base + url, { method, headers: { Origin: origin, Cookie: cookies[index], ...(body && !(body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}) }, body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined });
  return { status: response.status, ...(await response.json()) };
}
function ok(result, status = 200) { assert.equal(result.status, status, JSON.stringify(result)); return result.data; }
async function check(name, run) { await run(); checks.push(name); console.log('PASS ' + name); }
async function eventually(read, label, attempts = 60) { for (let i = 0; i < attempts; i++) { const value = await read(); if (value) return value; await wait(150); } throw Error('Timed out: ' + label); }
try {
  for (const port of [8101, 8102, 8103, 8104]) await eventually(async () => (await fetch(`http://127.0.0.1:${port}/actuator/health`).catch(() => null))?.status === 200, 'service ' + port, 200);
  await redis.connect();
  userDb = await connect('user'); contentDb = await connect('content'); growthDb = await connect('growth');
  const [[admin]] = await userDb.execute('SELECT password_hash FROM user WHERE username=?', [process.env.MENG_ADMIN_USERNAME]);
  for (let i = 0; i < 3; i++) {
    const username = 'verify_social_' + nonce + '_' + i;
    const [insert] = await userDb.execute("INSERT INTO user(username,password_hash,role,status,level,nickname) VALUES(?,?,'USER','NORMAL',1,?)", [username, admin.password_hash, ['甲', '乙', '丙'][i] + nonce]);
    users.push(insert.insertId);
    const ticket = crypto.randomUUID(), key = 'meng:captcha-ticket:' + ticket; keys.push(key);
    await redis.set(key, crypto.createHmac('sha256', Buffer.from(process.env.MENG_PII_HASH_KEY, 'base64')).update('127.0.0.1').digest('hex'), { EX: 120 });
    const response = await fetch(base + '/user/sessions', { method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password: process.env.MENG_ADMIN_PASSWORD, ticket }) });
    assert.equal(response.status, 200);
    cookies.push(response.headers.get('set-cookie').split(';')[0]);
  }
  const [a, b, c] = users;
  await check('Follow is idempotent, rejects self and unknown accounts, and keeps counters exact', async () => {
    ok(await api('PUT', `/user/users/${b}/follow`));
    ok(await api('PUT', `/user/users/${b}/follow`));
    const self = await api('PUT', `/user/users/${a}/follow`); ok(self, 409); assert.equal(self.code, 'USER_FOLLOW_SELF');
    ok(await api('PUT', '/user/users/999999999/follow'), 404);
    const profile = ok(await api('GET', `/user/users/${b}`));
    assert.equal(profile.followerCount, 1); assert.equal(profile.followed, true); assert.equal(profile.followsMe, false); assert.equal(profile.self, false);
    assert.equal(profile.username, undefined); assert.equal(profile.email, undefined);
    assert.equal(ok(await api('GET', '/user/me')).followingCount, 1);
    ok(await api('PUT', `/user/users/${a}/follow`, null, 1));
    const back = ok(await api('GET', `/user/users/${a}`, null, 1));
    assert.equal(back.followed, true); assert.equal(back.followsMe, true); assert.equal(back.self, false);
    assert.equal(ok(await api('GET', `/user/users/${a}`)).self, true);
  });
  await check('Following and follower lists carry the relation flags and paginate', async () => {
    ok(await api('PUT', `/user/users/${b}/follow`, null, 2));
    const followers = ok(await api('GET', `/user/users/${b}/followers`));
    assert.equal(followers.total, 2); assert.deepEqual(followers.records.map((r) => r.id), [c, a]);
    const mine = followers.records.find((r) => r.id === a); assert.equal(mine.self, true);
    assert.equal(followers.records.find((r) => r.id === c).followed, false);
    const first = ok(await api('GET', `/user/users/${b}/followers?page=1&size=1`)); assert.equal(first.records.length, 1); assert.equal(first.total, 2);
    const following = ok(await api('GET', `/user/users/${a}/following`)); assert.deepEqual(following.records.map((r) => r.id), [b]); assert.equal(following.records[0].followsMe, true);
    ok(await api('GET', `/user/users/${b}/followers?size=51`), 400);
  });
  await check('Unfollow, refollow and notifications: one FOLLOW notice per pair', async () => {
    ok(await api('DELETE', `/user/users/${b}/follow`)); ok(await api('DELETE', `/user/users/${b}/follow`));
    assert.equal(ok(await api('GET', `/user/users/${b}`)).followerCount, 1);
    ok(await api('PUT', `/user/users/${b}/follow`));
    assert.equal(ok(await api('GET', `/user/users/${b}`)).followerCount, 2);
    const notices = await eventually(async () => { const rows = ok(await api('GET', '/im/notifications?channel=SYSTEM&category=FOLLOW', null, 1)).records; return rows.length >= 2 ? rows : null; }, 'follow notices');
    await wait(500);
    const settled = ok(await api('GET', '/im/notifications?channel=SYSTEM&category=FOLLOW', null, 1)).records;
    assert.equal(settled.length, 2); assert.ok(settled.every((r) => r.notice.type === 'FOLLOWED')); assert.deepEqual(settled.map((r) => r.actors[0].id).sort(), [a, c].sort());
    assert.ok(notices.length >= 2);
    const groups = ok(await api('GET', '/im/notification-groups?channel=SYSTEM', null, 1)); assert.ok(groups.some((g) => g.key === 'FOLLOW' && g.total === 2));
    assert.equal(ok(await api('GET', '/im/notifications?channel=SYSTEM&category=FOLLOW', null, 0)).records.filter((r) => r.notice.type === 'FOLLOWED').length, 1);
  });
  let post, folder;
  await check('Folders are private by default; visibility is switchable and gates the profile views', async () => {
    await redis.del('meng:content:limit:post-interval:' + a);
    post = ok(await api('POST', '/content/posts', { category: 'DAILY', title: '主页验证 ' + nonce, content: '公开收藏夹', imageIds: [], tags: [] }));
    const form = new FormData(); form.append('name', '公开测试'); form.append('description', '');
    folder = ok(await api('POST', '/content/favorite-folders', form));
    const mineList = ok(await api('GET', '/content/favorite-folders'));
    assert.ok(mineList.records.every((r) => r.visibility === 'PRIVATE'));
    ok(await api('PUT', `/content/favorite-folders/${folder}/posts/${post}`));
    assert.equal(ok(await api('GET', `/content/users/${a}/favorite-folders`, null, 1)).total, 0);
    ok(await api('GET', `/content/users/${a}/favorite-folders/${folder}/posts`, null, 1), 404);
    ok(await api('PUT', `/content/favorite-folders/${folder}/visibility`, { visibility: 'PUBLIC' }, 1), 403);
    ok(await api('PUT', `/content/favorite-folders/${folder}/visibility`, { visibility: 'SECRET' }), 400);
    ok(await api('PUT', `/content/favorite-folders/${folder}/visibility`, { visibility: 'PUBLIC' }));
    const seen = ok(await api('GET', `/content/users/${a}/favorite-folders`, null, 1));
    assert.equal(seen.total, 1); assert.equal(seen.records[0].id, folder); assert.equal(seen.records[0].visibility, 'PUBLIC'); assert.equal(seen.records[0].count, 1);
    assert.equal(ok(await api('GET', `/content/users/${a}/favorite-folders/${folder}/posts`, null, 1)).records[0].id, post);
    const counts = ok(await api('GET', `/content/users/${a}/counts`, null, 1)); assert.deepEqual(counts, { posts: 1, folders: 1, favorites: 1 });
    ok(await api('PUT', `/content/favorite-folders/${folder}/visibility`, { visibility: 'PRIVATE' }));
    assert.equal(ok(await api('GET', `/content/users/${a}/favorite-folders`, null, 1)).total, 0);
    ok(await api('GET', `/content/users/${a}/favorite-folders/${folder}/posts`, null, 1), 404);
    const defaultFolder = mineList.records.find((r) => r.isDefault).id;
    ok(await api('PUT', `/content/favorite-folders/${defaultFolder}/visibility`, { visibility: 'PUBLIC' }));
    assert.equal(ok(await api('GET', `/content/users/${a}/favorite-folders`, null, 1)).total, 1);
    ok(await api('PUT', `/content/favorite-folders/${defaultFolder}/visibility`, { visibility: 'PRIVATE' }));
  });
  await check('Profile posts list shows the author posts only', async () => {
    const list = ok(await api('GET', `/content/users/${a}/posts`, null, 1)); assert.equal(list.total, 1); assert.equal(list.records[0].id, post);
    assert.equal(ok(await api('GET', `/content/users/${b}/posts`, null, 0)).total, 0);
    ok(await api('GET', `/content/users/${a}/posts?size=51`, null, 1), 400);
  });
  await check('Points: sign-in, posts and comments earn once, with ledger and rules', async () => {
    assert.equal(ok(await api('GET', '/growth/points', null, 2)).balance, 0);
    ok(await api('POST', '/growth/check-ins', null, 2));
    const afterSignIn = ok(await api('GET', '/growth/points', null, 2)); assert.equal(afterSignIn.balance, 5);
    assert.deepEqual(afterSignIn.rules.map((r) => [r.source, r.amount, r.dailyMax]), [['CHECK_IN', 5, 1], ['POST', 10, 3], ['COMMENT', 2, 10]]);
    ok(await api('POST', '/growth/check-ins', null, 2), 409);
    assert.equal(ok(await api('GET', '/growth/points', null, 2)).balance, 5);
    await redis.del('meng:content:limit:post-interval:' + c);
    const own = ok(await api('POST', '/content/posts', { category: 'DAILY', title: '积分验证 ' + nonce, content: '发帖得积分', imageIds: [], tags: [] }, 2));
    await eventually(async () => ok(await api('GET', '/growth/points', null, 2)).balance === 15, 'post points');
    await redis.del('meng:content:limit:comment-interval:' + c);
    ok(await api('POST', `/content/posts/${own}/comments`, { content: '评论得积分' }, 2));
    await eventually(async () => ok(await api('GET', '/growth/points', null, 2)).balance === 17, 'comment points');
    const ledger = ok(await api('GET', '/growth/point-records', null, 2));
    assert.equal(ledger.total, 3); assert.deepEqual(ledger.records.map((r) => [r.source, r.amount]), [['COMMENT', 2], ['POST', 10], ['CHECK_IN', 5]]);
    assert.equal(ok(await api('GET', '/growth/point-records?source=POST', null, 2)).total, 1);
    assert.equal(ok(await api('GET', '/growth/point-records?page=2&size=2', null, 2)).records.length, 1);
    ok(await api('GET', '/growth/point-records?source=GIFT', null, 2), 400);
    const stats = ok(await api('GET', '/growth/point-stats?days=7', null, 2));
    assert.equal(stats.days.length, 7); assert.equal(stats.days.at(-1).amount, 17);
    assert.deepEqual(stats.sources.map((r) => [r.source, r.amount]), [['CHECK_IN', 5], ['POST', 10], ['COMMENT', 2]]);
    assert.equal(ok(await api('GET', '/growth/point-stats?days=30', null, 2)).days.length, 30);
    ok(await api('GET', '/growth/point-stats?days=8', null, 2), 400);
    ok(await api('GET', '/growth/point-records?size=51', null, 2), 400);
    assert.equal(ok(await api('GET', '/growth/points', null, 0)).balance, 10);
    const exp = ok(await api('GET', '/growth/me', null, 2)); assert.equal(exp.exp, 17);
  });
  console.log(JSON.stringify({ passed: true, checks: checks.length }));
} finally {
  if (contentDb && users.length) {
    const [posts] = await contentDb.query('SELECT id FROM post WHERE author_id IN (?)', [users]);
    const ids = posts.map((p) => p.id);
    const [comments] = await contentDb.query('SELECT id FROM comment WHERE author_id IN (?)', [users]);
    if (comments.length) await contentDb.query('DELETE FROM comment_mention WHERE comment_id IN (?)', [comments.map((c) => c.id)]);
    await contentDb.query('DELETE FROM comment WHERE author_id IN (?)', [users]);
    if (ids.length) for (const table of ['post_tag', 'post_like', 'post_favorite']) await contentDb.query(`DELETE FROM ${table} WHERE post_id IN (?)`, [ids]);
    await contentDb.query('DELETE FROM post WHERE author_id IN (?)', [users]);
    await cleanupPostReadModels(redis, ids);
    await contentDb.query('DELETE FROM favorite_folder WHERE user_id IN (?)', [users]);
    await contentDb.query('DELETE FROM author_daily_stat WHERE author_id IN (?)', [users]);
  }
  await contentDb?.end();
  if (growthDb && users.length) for (const table of ['exp_record', 'check_in', 'growth_account', 'point_record', 'point_account']) await growthDb.query(`DELETE FROM ${table} WHERE user_id IN (?)`, [users]);
  await growthDb?.end();
  if (redis.isOpen) {
    for (const cookie of cookies) { const claims = JSON.parse(Buffer.from(cookie.split('=')[1].split('.')[1], 'base64url')); await redis.del('meng:session:' + claims.sessionId); }
    for (const user of users) {
      await redis.del('meng:user-sessions:' + user);
      for (const prefix of ['meng:content:', 'meng:im:rate:']) for await (const batch of redis.scanIterator({ MATCH: prefix + '*', COUNT: 200 })) for (const key of batch) if (key.endsWith(':' + user)) await redis.del(key);
    }
    for (const key of keys) await redis.del(key);
    await redis.quit();
  }
  await cleanupImFixtures(users);
  if (userDb && users.length) {
    await userDb.query('DELETE FROM user_follow WHERE follower_id IN (?) OR followee_id IN (?)', [users, users]);
    await userDb.query('DELETE FROM user WHERE id IN (?)', [users]);
  }
  await userDb?.end();
}
