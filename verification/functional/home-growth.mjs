import { cleanupPostReadModels } from './read-model-cleanup.mjs';
import fs from "node:fs/promises";
import crypto from "node:crypto";
import assert from "node:assert/strict";
import mysql from "mysql2/promise";
import amqp from "amqplib";
import { createClient } from "redis";
import { chromium } from "playwright";
// Only randomly named fixture accounts, their posts, events and Redis keys are changed.
const origin = "http://127.0.0.1:5173",
  base = "http://127.0.0.1:8080";
const report = {
  passed: false,
  checks: [],
  browser: "Browser plugin not available; Edge Playwright",
  console: [],
};
const nonce = crypto.randomBytes(5).toString("hex"),
  names = ["verify_home_" + nonce + "a", "verify_home_" + nonce + "b"],
  users = [],
  posts = [],
  files = [],
  cookies = [],
  keys = [];
const redis = createClient({
  url: "redis://127.0.0.1:" + process.env.MENG_REDIS_PORT,
  password: process.env.MENG_REDIS_PASSWORD,
});
redis.on("error", () => {});
let userDb, contentDb, growthDb, broker, ch, browser;
async function db(domain) {
  return mysql.createConnection({
    host: "127.0.0.1",
    port: Number(process.env.MENG_MYSQL_PORT),
    user: "meng_" + domain + "_app",
    password: process.env["MENG_MYSQL_" + domain.toUpperCase() + "_PASSWORD"],
    database: "meng_" + domain,
  });
}
async function api(method, path, body, index = 0, headers = {}) {
  const res = await fetch(base + path, {
    method,
    headers: {
      Origin: origin,
      ...(cookies[index] ? { Cookie: cookies[index] } : {}),
      ...(body && !(body instanceof FormData)
        ? { "Content-Type": "application/json" }
        : {}),
      ...headers,
    },
    body:
      body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(15000),
  });
  const json = await res.json();
  return {
    status: res.status,
    ...json,
    cookie: res.headers.get("set-cookie")?.split(";")[0],
  };
}
function ok(r, status = 200) {
  assert.equal(
    r.status,
    status,
    JSON.stringify({ status: r.status, code: r.code, message: r.message }),
  );
  return r.data;
}
async function check(name, fn) {
  await fn();
  report.checks.push(name);
  process.stdout.write("Passed: " + name + "\n");
}
async function eventually(fn) {
  for (let i = 0; i < 100; i++) {
    if (await fn()) return;
    await new Promise((r) => setTimeout(r, 100));
  }
  throw Error("Event propagation timeout");
}
function event(type, body, id = crypto.randomUUID()) {
  return {
    eventId: id,
    eventType: type,
    traceId: id,
    occurredAt: new Date().toISOString(),
    body,
  };
}
async function publish(type, body, id) {
  ch.publish(
    type.startsWith("growth.") ? "meng.growth.topic" : "meng.content.topic",
    type,
    Buffer.from(JSON.stringify(event(type, body, id))),
    { contentType: "application/json", deliveryMode: 2 },
  );
  await ch.waitForConfirms();
}
try {
  await fs.mkdir(new URL("../test-output/home-growth/", import.meta.url), {
    recursive: true,
  });
  await redis.connect();
  userDb = await db("user");
  contentDb = await db("content");
  growthDb = await db("growth");
  const [admins] = await userDb.execute(
    "SELECT password_hash FROM user WHERE username=?",
    [process.env.MENG_ADMIN_USERNAME],
  );
  assert.equal(admins.length, 1);
  for (let i = 0; i < 2; i++) {
    const [result] = await userDb.execute(
      "INSERT INTO user(username,password_hash,role,status,level) VALUES(?,?,'USER','NORMAL',1)",
      [names[i], admins[0].password_hash],
    );
    users.push(result.insertId);
    const ticket = crypto.randomUUID(),
      key = "meng:captcha-ticket:" + ticket;
    keys.push(key);
    const iphash = crypto
      .createHmac(
        "sha256",
        Buffer.from(process.env.MENG_PII_HASH_KEY, "base64"),
      )
      .update("127.0.0.1")
      .digest("hex");
    await redis.set(key, iphash, { EX: 120 });
    const response = await api(
      "POST",
      "/user/sessions",
      { username: names[i], password: process.env.MENG_ADMIN_PASSWORD, ticket },
      i,
    );
    ok(response);
    cookies.push(response.cookie);
  }
  broker = await amqp.connect({
    hostname: "127.0.0.1",
    port: Number(process.env.MENG_RABBITMQ_PORT),
    username: process.env.MENG_RABBITMQ_USERNAME,
    password: process.env.MENG_RABBITMQ_PASSWORD,
    vhost: "meng",
  });
  ch = await broker.createConfirmChannel();
  await check(
    "All content and growth routes require a gateway session",
    async () => {
      for (const path of [
        "/content/posts",
        "/growth/me",
        "/content/files/fake.png",
      ]) {
        const r = await fetch(base + path);
        assert.equal(r.status, 401);
      }
    },
  );
  await check(
    "Existing users lazily receive level 1, zero experience; concurrent check-in only rewards once",
    async () => {
      let d = ok(await api("GET", "/growth/me"));
      assert.equal(d.level, 1);
      assert.equal(d.exp, 0);
      assert.equal(d.week.length, 7);
      const responses = await Promise.all([
        api("POST", "/growth/check-ins"),
        api("POST", "/growth/check-ins"),
      ]);
      assert.deepEqual(responses.map((x) => x.status).sort(), [200, 409]);
      d = ok(await api("GET", "/growth/me"));
      assert.equal(d.exp, 5);
      assert.equal(d.checkedIn, true);
      assert.equal(
        d.week.filter((x) => x.date === d.today && x.checkedIn).length,
        1,
      );
      assert.equal(ok(await api("GET", "/growth/me", null, 1)).exp, 0);
    },
  );
  await check(
    "Fake extension and files above 5 MB are rejected; real JPEG, PNG, GIF and WebP upload and thumbnail read succeed",
    async () => {
      const form = new FormData();
      form.set(
        "file",
        new Blob(["not image"], { type: "image/png" }),
        "fake.png",
      );
      ok(await api("POST", "/content/images", form), 400);
      const big = new FormData();
      big.set(
        "file",
        new Blob([new Uint8Array(5 * 1024 * 1024 + 1)], { type: "image/png" }),
        "big.png",
      );
      const r = await api("POST", "/content/images", big);
      assert.ok([400, 413].includes(r.status));
      const png = Buffer.from(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aV1sAAAAASUVORK5CYII=",
        "base64",
      );
      const samples = [
        { name: "tiny.png", data: png },
        {
          name: "tiny.gif",
          data: Buffer.from(
            "R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==",
            "base64",
          ),
        },
      ];
      const webpFile = (
        await fs.readdir(
          new URL("../../vue_front/front_user/dist/assets/", import.meta.url),
        )
      ).find((n) => n.endsWith(".webp"));
      samples.push({
        name: "tiny.webp",
        data: await fs.readFile(
          new URL(
            "../../vue_front/front_user/dist/assets/" + webpFile,
            import.meta.url,
          ),
        ),
      });
      samples.push({
        name: "captcha.jpg",
        data: await fs.readFile(
          new URL(
            "../../java_backend/meng-user/src/main/resources/captcha/bg-1.jpg",
            import.meta.url,
          ),
        ),
      });
      for (const sample of samples) {
        const form = new FormData();
        form.set("file", new Blob([sample.data]), sample.name);
        const image = ok(await api("POST", "/content/images", form));
        files.push(image);
        const response = await fetch(base + image.thumbUrl, {
          headers: { Cookie: cookies[0] },
        });
        assert.equal(response.status, 200);
        assert.equal(response.headers.get("content-type"), "image/png");
        assert.ok((await response.arrayBuffer()).byteLength > 0);
      }
    },
  );
  await check(
    "Create validates fields and ownership; real categorized feed, summaries, author fallback and trusted IP work",
    async () => {
      const body = {
        category: "DAILY",
        title: "今天的云像棉花糖一样",
        content: "# 萌部落\n\n**喜欢**和大家分享今天的天空。",
        imageIds: files.map((x) => x.id),
      };
      ok(
        await api("POST", "/content/posts", {
          ...body,
          title: "萌".repeat(51),
        }),
        400,
      );
      ok(
        await api("POST", "/content/posts", {
          ...body,
          content: "萌".repeat(10001),
        }),
        400,
      );
      ok(await api("POST", "/content/posts", { ...body, category: null }), 400);
      ok(
        await api("POST", "/content/posts", {
          ...body,
          imageIds: Array(10).fill(files[0].id),
        }),
        400,
      );
      ok(await api("POST", "/content/posts", body, 1), 403);
      for (let i = 0; i < 4; i++) {
        await redis.del("meng:content:limit:post-interval:" + users[0]);
        posts.push(
          ok(
            await api(
              "POST",
              "/content/posts",
              {
                ...body,
                title: body.title + " " + i,
                imageIds: i ? [] : body.imageIds,
              },
              0,
              { "X-Client-IP": "8.8.8.8" },
            ),
          ),
        );
      }
      ok(await api("GET", "/content/posts?size=51"), 400);
      const d = ok(await api("GET", "/content/posts?category=DAILY&size=1"));
      assert.ok(d.total >= 4);
      assert.equal(d.records.length, 1);
      const first = d.records[0];
      assert.equal(first.author.nickname, names[0]);
      assert.equal(first.ipRegion, undefined);
      assert.equal(first.viewCount, 0);
      assert.equal(first.commentCount, 0);
      assert.ok(!first.summary.includes("**"));
      assert.equal(first.id, posts[3]);
      assert.ok(
        !ok(await api("GET", "/content/posts?category=ANIME")).records.some(
          (p) => posts.includes(p.id),
        ),
      );
      await eventually(
        async () => ok(await api("GET", "/growth/me")).exp === 35,
      );
    },
  );
  await check(
    "Repeated likes and favorites are idempotent, counters atomic, viewer states private; hot excludes old posts",
    async () => {
      const id = posts[0],
        path = "/content/posts/" + id;
      await Promise.all(
        Array.from({ length: 8 }, () => api("PUT", path + "/like").then(ok)),
      );
      ok(await api("PUT", path + "/like", null, 1));
      await Promise.all([
        api("PUT", path + "/favorite").then(ok),
        api("PUT", path + "/favorite").then(ok),
      ]);
      const feed = ok(await api("GET", "/content/posts")),
        a = feed.records.find((p) => p.id === id),
        other = ok(await api("GET", "/content/posts", null, 1)).records.find(
          (p) => p.id === id,
        );
      assert.equal(a.likeCount, 2);
      assert.equal(a.liked, true);
      assert.equal(a.favorited, true);
      assert.equal(other.favorited, false);
      await Promise.all([
        api("DELETE", path + "/like").then(ok),
        api("DELETE", path + "/like").then(ok),
      ]);
      assert.equal(
        ok(await api("GET", "/content/posts")).records.find((p) => p.id === id)
          .likeCount,
        1,
      );
      await contentDb.execute(
        "UPDATE post SET create_time=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 8 DAY) WHERE id=?",
        [posts[3]],
      );
      assert.ok(
        !ok(await api("GET", "/content/posts?sort=HOT")).records.some(
          (p) => p.id === posts[3],
        ),
      );
      await redis.del("meng:content:hot:active");
      const [[hottest]] = await contentDb.execute(
        "SELECT id FROM post WHERE delete_state=0 AND create_time>=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 7 DAY) ORDER BY hot_score DESC,id DESC LIMIT 1",
      );
      assert.equal(
        ok(await api("GET", "/content/posts?sort=HOT")).records[0].id,
        hottest.id,
      );
      const [[scores]] = await contentDb.execute(
        "SELECT a.hot_score AS liked,b.hot_score AS untouched FROM post a JOIN post b ON b.id=? WHERE a.id=?",
        [posts[1], id],
      );
      assert.ok(scores.liked > scores.untouched);
    },
  );
  await check(
    "Duplicate events reward once; post/comment daily caps hold; upgrade projection is monotone",
    async () => {
      const id = crypto.randomUUID();
      await publish(
        "post.published",
        { postId: posts[0], authorId: users[0] },
        id,
      );
      await publish(
        "post.published",
        { postId: posts[0], authorId: users[0] },
        id,
      );
      await Promise.all([
        (async () => {
          for (let i = 0; i < 11; i++)
            await publish("comment.created", {
              commentId: Number(users[0]) * 1000 + i,
              postId: posts[0],
              postTitle: "test",
              authorId: users[0],
              targetUserId: null,
            });
        })(),
        (async () => {
          for (let i = 0; i < 10; i++)
            await Promise.all(
              Array.from({ length: 6 }, () =>
                api("GET", "/growth/me").then(ok),
              ),
            );
        })(),
      ]);
      await eventually(
        async () => ok(await api("GET", "/growth/me")).exp === 55,
      );
      assert.equal(ok(await api("GET", "/growth/me")).level, 2);
      await eventually(async () => {
        const [r] = await userDb.execute("SELECT level FROM user WHERE id=?", [
          users[0],
        ]);
        return r[0].level === 2;
      });
      await publish("growth.level-changed", { userId: users[0], level: 1 });
      await new Promise((r) => setTimeout(r, 200));
      const [r] = await userDb.execute("SELECT level FROM user WHERE id=?", [
        users[0],
      ]);
      assert.equal(r[0].level, 2);
      const [ledger] = await growthDb.execute(
        "SELECT source,COUNT(*) n FROM exp_record WHERE user_id=? GROUP BY source",
        [users[0]],
      );
      assert.equal(ledger.find((x) => x.source === "POST").n, 3);
      assert.equal(ledger.find((x) => x.source === "COMMENT").n, 10);
    },
  );
  await check(
    "Real homepage rendering, filters persist across reload/back, optimistic actions and mobile layout",
    async () => {
      browser = await chromium.launch({ channel: "msedge", headless: true });
      const context = await browser.newContext({
        viewport: { width: 1440, height: 1080 },
      });
      await context.addCookies([
        {
          name: "meng_token",
          value: cookies[0].split("=")[1],
          url: origin,
          httpOnly: true,
          sameSite: "Lax",
        },
      ]);
      const page = await context.newPage();
      page.on("pageerror", (e) => report.console.push(e.message));
      page.on("console", (e) => {
        if (e.type() === "error" && !/401|409/.test(e.text()))
          report.console.push(e.text());
      });
      await page.goto(origin + "/?sort=HOT&page=1");
      await page.locator(".post-card").first().waitFor();
      await page.evaluate(() => document.fonts.ready);
      assert.equal(await page.title(), "首页 · 萌部落");
      assert.equal(await page.locator("vite-error-overlay").count(), 0);
      const paging = await page.evaluate(async () => {
        const { feedPages } = await import("/src/composables/feedPages.js");
        return [
          feedPages(1, 2),
          feedPages(1, 12),
          feedPages(6, 12),
          feedPages(12, 12),
        ];
      });
      assert.deepEqual(paging, [
        [1, 2],
        [1, 2, 3, 4, 5, null, 12],
        [1, null, 5, 6, 7, null, 12],
        [1, null, 8, 9, 10, 11, 12],
      ]);
      const growthData = ok(await api("GET", "/growth/me"));
      await page.route("**/api/growth/me", (route) =>
        route.fulfill({
          json: {
            code: "SUCCESS",
            data: {
              ...growthData,
              level: 3,
              levelStartExp: 150,
              nextLevelExp: 350,
            },
          },
        }),
      );
      await page.evaluate(() =>
        document.dispatchEvent(new Event("visibilitychange")),
      );
      await page.getByText("升级啦！现在是 3 级", { exact: true }).waitFor();
      assert.equal(
        await page.getByText("升级啦！现在是 3 级", { exact: true }).count(),
        1,
      );
      await page.unroute("**/api/growth/me");
      const animeResponse = page.waitForResponse(
        (response) =>
          response.url().includes("/api/content/posts?") &&
          response.url().includes("category=ANIME") &&
          response.status() === 200,
      );
      await page.getByRole("button", { name: "追番", exact: true }).click();
      await page.waitForURL("**category=ANIME**");
      const anime = (await (await animeResponse).json()).data;
      assert.ok(anime.records.every((post) => post.category === "ANIME"));
      if (anime.records.length)
        await page.locator(".post-type.category-anime").first().waitFor();
      else await page.getByText("这里还空空的", { exact: true }).waitFor();
      const reloadResponse = page.waitForResponse(
        (response) =>
          response.url().includes("/api/content/posts?") &&
          response.url().includes("category=ANIME") &&
          response.status() === 200,
      );
      await page.reload();
      assert.ok(
        (await (await reloadResponse).json()).data.records.every(
          (post) => post.category === "ANIME",
        ),
      );
      if (anime.records.length)
        await page.locator(".post-type.category-anime").first().waitFor();
      else await page.getByText("这里还空空的", { exact: true }).waitFor();
      await page.goBack();
      await page.locator(".post-card").first().waitFor();
      await page
        .getByRole("button", { name: "喜欢", exact: true })
        .first()
        .click();
      await page
        .getByRole("button", { name: "取消喜欢", exact: true })
        .first()
        .waitFor();
      assert.equal(await page.locator(".home-sidebar .collapsible-card").count(), 2);
      const toggles = page.locator(".home-sidebar .collapse-toggle");
      await toggles.nth(0).click();
      await toggles.nth(1).click();
      await page.locator(".home-columns.sidebar-compact").waitFor();
      await toggles.nth(0).click();
      await page.locator(".home-columns.sidebar-compact").waitFor({state: "hidden"});
      await toggles.nth(1).click();
      await page.evaluate(() => window.scrollTo(0, 0));
      await page.mouse.move(1439, 1079);
      await page.waitForTimeout(1200);
      await page.screenshot({
        path: new URL(
          "../test-output/home-growth/desktop.png",
          import.meta.url,
        ).pathname.replace(/^\//, ""),
      });
      await page.setViewportSize({ width: 390, height: 844 });
      await page.evaluate(() => window.scrollTo(0, 0));
      await page.waitForTimeout(300);
      await page.screenshot({
        path: new URL(
          "../test-output/home-growth/mobile.png",
          import.meta.url,
        ).pathname.replace(/^\//, ""),
      });
      assert.ok(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth + 1,
        ),
      );
      assert.deepEqual(report.console, []);
    },
  );
  report.passed = true;
} finally {
  if (browser) await browser.close();
  if (ch) await ch.close();
  if (broker) await broker.close();
  if (contentDb) {
    for (const user of users) {
      await contentDb.execute("DELETE FROM post_like WHERE user_id=?", [user]);
      await contentDb.execute("DELETE FROM post_favorite WHERE user_id=?", [
        user,
      ]);
      const [images] = await contentDb.execute(
        "SELECT file_name,thumb_name FROM post_image WHERE uploader_id=?",
        [user],
      );
      for (const image of images)
        for (const n of [image.file_name, image.thumb_name])
          await fs.rm(process.env.MENG_CONTENT_IMAGE_ROOT + "/" + n, {
            force: true,
          });
      await contentDb.execute("DELETE FROM post_image WHERE uploader_id=?", [
        user,
      ]);
      const [fixtures] = await contentDb.execute("SELECT id FROM post WHERE author_id=?", [user]);
      await contentDb.execute("DELETE FROM post WHERE author_id=?", [user]);
      await contentDb.execute("DELETE FROM author_daily_stat WHERE author_id=?", [user]);
      await cleanupPostReadModels(redis, fixtures.map(post => post.id));
    }
    await contentDb.end();
  }
  if (growthDb) {
    for (const user of users)
      for (const table of ["exp_record", "check_in", "growth_account"])
        await growthDb.execute("DELETE FROM " + table + " WHERE user_id=?", [
          user,
        ]);
    await growthDb.end();
  }
  if (redis.isOpen) {
    for (const cookie of cookies) {
      const claims = JSON.parse(
        Buffer.from(cookie.split("=")[1].split(".")[1], "base64url"),
      );
      await redis.del("meng:session:" + claims.sessionId);
    }
    for (const user of users) {
      await redis.del("meng:user-sessions:" + user);
      for await (const batch of redis.scanIterator({
        MATCH: "meng:content:limit:*:" + user,
        COUNT: 100,
      }))
        for (const key of batch) await redis.del(key);
    }
    for (const key of keys) await redis.del(key);
    await redis.quit();
  }
  if (userDb) {
    for (const user of users)
      await userDb.execute("DELETE FROM user WHERE id=?", [user]);
    await userDb.end();
  }
  await fs.writeFile(
    new URL("../test-output/home-growth/report.json", import.meta.url),
    JSON.stringify(report, null, 2),
  );
  process.stdout.write(JSON.stringify(report) + "\n");
}
