import { cleanupPostReadModels } from "./read-model-cleanup.mjs";
import fs from "node:fs/promises";
import crypto from "node:crypto";
import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { performance } from "node:perf_hooks";
import mysql from "mysql2/promise";
import { createClient } from "redis";
import { chromium } from "playwright";
const origin = "http://127.0.0.1:5173",
  base = "http://127.0.0.1:8080",
  keyword = "s" + crypto.randomBytes(3).toString("hex"),
  users = [],
  cookies = [],
  postIds = [],
  checks = [],
  measurements = {};
const redis = createClient({
  url: `redis://127.0.0.1:${process.env.MENG_REDIS_PORT}`,
  password: process.env.MENG_REDIS_PASSWORD,
});
const connect = (domain) =>
  mysql.createConnection({
    host: "127.0.0.1",
    port: Number(process.env.MENG_MYSQL_PORT),
    user: `meng_${domain}_app`,
    password: process.env[`MENG_MYSQL_${domain.toUpperCase()}_PASSWORD`],
    database: `meng_${domain}`,
  });
const esBase = `http://127.0.0.1:${process.env.MENG_ES_PORT}`,
  esHeaders = {
    Authorization:
      "Basic " +
      Buffer.from(
        `meng_content_app:${process.env.MENG_ES_CONTENT_PASSWORD}`,
      ).toString("base64"),
  };
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
let userDb, contentDb, growthDb, browser;
async function api(method, url, body, index = 0) {
  const r = await fetch(base + url, {
    method,
    headers: {
      Origin: origin,
      ...(cookies[index] ? { Cookie: cookies[index] } : {}),
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: r.status, ...(await r.json()) };
}
function ok(r, status = 200) {
  assert.equal(r.status, status, JSON.stringify(r));
  return r.data;
}
async function check(name, run) {
  await run();
  checks.push(name);
  console.log("PASS " + name);
}
async function search(q = keyword, extra = "") {
  await redis.del("meng:content:limit:search:" + users[0]);
  return ok(
    await api(
      "GET",
      "/content/search/posts?q=" + encodeURIComponent(q) + extra,
    ),
  );
}
async function es(url, options = {}) {
  const r = await fetch(esBase + url, {
    ...options,
    headers: { ...esHeaders, ...options.headers },
  });
  assert.ok(r.ok, "Elasticsearch " + r.status);
  return r.json();
}
async function poll(fn) {
  for (let i = 0; i < 100; i++) {
    if (await fn()) return;
    await wait(100);
  }
  throw Error("Polling timed out");
}
const payload = (
  title,
  content = "星河的每一天，记录新的发现。",
  category = "DAILY",
  tags = [],
) => ({ title, content, category, tags, imageIds: [] });
async function create(body) {
  await redis.del("meng:content:limit:post-interval:" + users[0]);
  const id = ok(await api("POST", "/content/posts", body));
  postIds.push(id);
  return id;
}
async function alias() {
  return Object.keys(await es("/_alias/meng_post"))[0];
}
try {
  await redis.connect();
  userDb = await connect("user");
  contentDb = await connect("content");
  growthDb = await connect("growth");
  const [[admin]] = await userDb.execute(
    "SELECT password_hash FROM user WHERE username=?",
    [process.env.MENG_ADMIN_USERNAME],
  );
  assert.ok(admin);
  for (let i = 0; i < 2; i++) {
    const username = keyword + "_" + i;
    const [r] = await userDb.execute(
      "INSERT INTO user(username,password_hash,role,status,level) VALUES(?,?,?,'NORMAL',1)",
      [username, admin.password_hash, i === 0 ? "ADMIN" : "USER"],
    );
    users.push(r.insertId);
    const ticket = crypto.randomUUID();
    await redis.set(
      "meng:captcha-ticket:" + ticket,
      crypto
        .createHmac(
          "sha256",
          Buffer.from(process.env.MENG_PII_HASH_KEY, "base64"),
        )
        .update("127.0.0.1")
        .digest("hex"),
      { EX: 120 },
    );
    const response = await fetch(base + "/user/sessions", {
      method: "POST",
      headers: { Origin: origin, "Content-Type": "application/json" },
      body: JSON.stringify({
        username,
        password: process.env.MENG_ADMIN_PASSWORD,
        ticket,
      }),
    });
    assert.equal(response.status, 200);
    cookies.push(response.headers.get("set-cookie").split(";")[0]);
  }
  await check("Authentication and query window validation", async () => {
    assert.equal(
      (await fetch(base + "/content/search/posts?q=test")).status,
      401,
    );
    for (const q of [" ", "🌟".repeat(51)])
      ok(
        await api("GET", "/content/search/posts?q=" + encodeURIComponent(q)),
        400,
      );
    ok(await api("GET", "/content/search/posts?q=x&page=2001&size=5"), 400);
    ok(await api("POST", "/content/admin/search/rebuild", null, 1), 403);
  });
  await check(
    "Published posts become searchable in title, body and tags",
    async () => {
      for (let i = 0; i < 12; i++)
        await create(
          payload(
            `${keyword} 星河 ${i}`,
            "分享星河与代码。",
            i % 2 ? "TECH" : "DAILY",
          ),
        );
      await create(
        payload("正文独有的词", "body" + keyword, "CREATION", [
          "tag" + keyword,
        ]),
      );
      await create(payload(`<img onerror=evil()> ${keyword}`, "安全正文"));
      await poll(async () => {
        await es("/meng_post/_refresh", { method: "POST" });
        return (await search()).total === 13;
      });
      assert.equal((await search("body" + keyword)).total, 1);
      assert.equal((await search("tag" + keyword)).total, 1);
      const data = await search(keyword, "&sort=LATEST");
      assert.equal(data.records.length, 5);
      assert.equal(data.records[0].id, postIds.at(-1));
      for (const row of data.records)
        assert.equal(row.titleSegments.map((s) => s.text).join(""), row.title);
    },
  );
  await check("Categories, pagination and safe text highlights", async () => {
    assert.equal((await search(keyword, "&category=TECH")).total, 6);
    const first = await search(keyword, "&sort=LATEST&page=1"),
      second = await search(keyword, "&sort=LATEST&page=2");
    assert.equal(second.records.length, 5);
    assert.ok(
      !first.records.some((a) => second.records.some((b) => b.id === a.id)),
    );
    const html = first.records.find((p) => p.title.startsWith("<img"));
    assert.ok(html);
    assert.ok(html.titleSegments.some((s) => s.matched));
    assert.equal(html.titleSegments.map((s) => s.text).join(""), html.title);
  });
  await check(
    "Favorite heat is idempotent and Redis excludes expired posts",
    async () => {
      const id = postIds[0];
      ok(await api("PUT", `/content/posts/${id}/favorite`));
      ok(await api("PUT", `/content/posts/${id}/favorite`));
      const [[post]] = await contentDb.execute(
        "SELECT hot_score FROM post WHERE id=?",
        [id],
      );
      assert.equal(post.hot_score, 5);
      const active = await redis.get("meng:content:hot:active");
      assert.equal(
        await redis.zScore(
          `meng:content:hot:${active}:ALL`,
          String(id).padStart(19, "0"),
        ),
        5,
      );
      assert.equal((await search()).records[0].id, id);
      const other = ok(
        await api("GET", `/content/search/posts?q=${keyword}`, null, 1),
      ).records.find((p) => p.id === id);
      assert.ok(other);
      assert.equal(other.favorited, false);
      ok(await api("DELETE", `/content/posts/${id}/favorite`));
      const [[after]] = await contentDb.execute(
        "SELECT hot_score FROM post WHERE id=?",
        [id],
      );
      assert.equal(after.hot_score, 0);
      const expired = postIds[1];
      await contentDb.execute(
        "UPDATE post SET create_time=UTC_TIMESTAMP()-INTERVAL 8 DAY WHERE id=?",
        [expired],
      );
      const old = active;
      ok(await api("POST", "/content/admin/hot/rebuild"));
      await poll(
        async () => (await redis.get("meng:content:hot:active")) !== old,
      );
      const current = await redis.get("meng:content:hot:active");
      assert.equal(
        await redis.zScore(
          `meng:content:hot:${current}:ALL`,
          String(expired).padStart(19, "0"),
        ),
        null,
      );
      ok(await api("PUT", `/content/posts/${expired}/like`));
      assert.equal(
        await redis.zScore(
          `meng:content:hot:${current}:ALL`,
          String(expired).padStart(19, "0"),
        ),
        null,
      );
    },
  );
  await check(
    "Editing and deleting repair search results asynchronously",
    async () => {
      const id = postIds[0];
      ok(
        await api(
          "PUT",
          `/content/posts/${id}`,
          payload("updated" + keyword.slice(1), "新的内容"),
        ),
      );
      await poll(async () => {
        await es("/meng_post/_refresh", { method: "POST" });
        return (
          (await search("updated" + keyword.slice(1))).records.some(
            (p) => p.id === id,
          ) &&
          !(await search(keyword, "&size=50")).records.some((p) => p.id === id)
        );
      });
      assert.ok(
        !(await search(keyword, "&size=50")).records.some((p) => p.id === id),
      );
      ok(await api("DELETE", `/content/posts/${id}`));
      await poll(async () => {
        await es("/meng_post/_refresh", { method: "POST" });
        return (await search("updated" + keyword.slice(1))).total === 0;
      });
    },
  );
  await check(
    "Blue green rebuild picks up direct database rows and keeps active reads",
    async () => {
      const direct = "direct" + keyword;
      const [insert] = await contentDb.execute(
        "INSERT INTO post(author_id,category,title,content,summary,image_count,view_count,like_count,comment_count,hot_score) VALUES(?,'DAILY','仅正文重建验证',?, ?,0,0,0,0,0)",
        [users[0], direct, direct],
      );
      postIds.push(insert.insertId);
      assert.equal((await search(direct)).total, 0);
      const old = await alias();
      ok(await api("POST", "/content/admin/search/rebuild"));
      for (let i = 0; i < 3; i++) assert.ok((await search()).total >= 10);
      await poll(async () => (await alias()) !== old);
      assert.equal((await search(direct)).total, 1);
      await poll(
        async () =>
          (await fetch(esBase + "/" + old, { headers: esHeaders })).status ===
          404,
      );
      assert.equal(
        (
          await fetch(esBase + "/unrelated_index/_count", {
            headers: esHeaders,
          })
        ).status,
        403,
      );
    },
  );
  await check(
    "Literal wildcard fallback and per-user search limit",
    async () => {
      const id = await create(payload("literal%_" + keyword));
      await es("/meng_post/_refresh", { method: "POST" });
      assert.ok((await search("%_")).records.some((p) => p.id === id));
      await redis.del("meng:content:limit:search:" + users[0]);
      for (let i = 0; i < 30; i++)
        ok(await api("GET", "/content/search/posts?q=" + keyword));
      ok(await api("GET", "/content/search/posts?q=" + keyword), 429);
      await redis.del("meng:content:limit:search:" + users[0]);
    },
  );
  if (process.env.MENG_SEARCH_FAULT_CHECK === "1")
    await check(
      "Elasticsearch outage preserves health and publishing with title fallback",
      async () => {
        const docker = promisify(execFile);
        await docker("docker", [
          "stop",
          "-t",
          "1",
          "meng_center-elasticsearch-1",
        ]);
        try {
          assert.equal(
            (await fetch("http://127.0.0.1:8102/actuator/health")).status,
            200,
          );
          const started = performance.now();
          assert.ok((await search()).total >= 10);
          measurements.outageFallback = Math.round(performance.now() - started);
          assert.equal((await search("body" + keyword)).total, 0);
          await create(payload("outage" + keyword));
        } finally {
          await docker("docker", ["start", "meng_center-elasticsearch-1"]);
          for (let attempt = 0; attempt < 90; attempt++) {
            try {
              await es("/meng_post/_count");
              break;
            } catch {
              if (attempt === 89) throw Error("Elasticsearch recovery failed");
              await wait(1000);
            }
          }
          const old = await alias();
          ok(await api("POST", "/content/admin/search/rebuild"));
          await poll(async () => (await alias()) !== old);
        }
      },
    );
  await check("Bounded parallel search and hot feed latency", async () => {
    await redis.del("meng:content:limit:search:" + users[0]);
    const samples = [],
      feeds = [];
    await redis.del("meng:content:limit:search:" + users[1]);
    for (let batch = 0; batch < 3; batch++)
      await Promise.all(
        Array.from({ length: 16 }, async (_, slot) => {
          let at = performance.now();
          ok(
            await api(
              "GET",
              "/content/search/posts?q=" + keyword,
              null,
              slot % 2,
            ),
          );
          samples.push(performance.now() - at);
          at = performance.now();
          ok(await api("GET", "/content/posts?sort=HOT", null, slot % 2));
          feeds.push(performance.now() - at);
        }),
      );
    const stats = (list) => {
      list.sort((a, b) => a - b);
      return {
        requests: list.length,
        p50: Math.round(list[Math.floor(list.length / 2)]),
        p95: Math.round(list[Math.ceil(list.length * 0.95) - 1]),
        max: Math.round(list.at(-1)),
      };
    };
    measurements.search = stats(samples);
    measurements.hotFeed = stats(feeds);
    assert.ok(measurements.search.p95 < 2000);
    await redis.del("meng:content:limit:search:" + users[0]);
  });
  await check(
    "Desktop and mobile search navigation, highlighting and motion",
    async () => {
      browser = await chromium.launch({ channel: "msedge", headless: true });
      const context = await browser.newContext({
        viewport: { width: 1440, height: 1000 },
      });
      const [name, value] = cookies[0].split("=");
      await context.addCookies([{ name, value, url: origin }]);
      const page = await context.newPage();
      const errors = [];
      page.on("pageerror", (e) => errors.push(e.message));
      await page.addInitScript(() => {
        window.__auroraDraws = 0;
        const draw = WebGL2RenderingContext.prototype.drawArrays;
        WebGL2RenderingContext.prototype.drawArrays = function (...arguments_) {
          if (this.canvas.closest(".aurora")) window.__auroraDraws++;
          return draw.apply(this, arguments_);
        };
      });
      await page.goto(origin + "/search?q=" + keyword + "&sort=LATEST&page=1");
      await page.locator(".post-card").first().waitFor();
      await page.locator(".aurora.ready canvas").waitFor();
      assert.ok(await page.locator(".aurora canvas").evaluate((canvas) => Math.max(canvas.width, canvas.height) <= 768));
      assert.ok(await page.locator(".aurora").evaluate((element) => element.getBoundingClientRect().height === innerHeight));
      assert.equal(await page.locator(".aurora").evaluate((element) => getComputedStyle(element).pointerEvents), "none");
      await wait(150);
      const beforeDraws = await page.evaluate(() => window.__auroraDraws);
      await wait(1000);
      measurements.auroraDrawsPerSecond = await page.evaluate(() => window.__auroraDraws) - beforeDraws;
      assert.ok(measurements.auroraDrawsPerSecond > 0 && measurements.auroraDrawsPerSecond <= 35);
      await page.evaluate(() => {
        Object.defineProperty(document, "hidden", {configurable: true, value: true});
        document.dispatchEvent(new Event("visibilitychange"));
      });
      const hiddenDraws = await page.evaluate(() => window.__auroraDraws);
      await wait(250);
      assert.equal(await page.evaluate(() => window.__auroraDraws), hiddenDraws);
      await page.evaluate(() => {
        delete document.hidden;
        document.dispatchEvent(new Event("visibilitychange"));
      });
      await page.evaluate(() => { window.__auroraCanvas = document.querySelector(".aurora canvas"); });
      assert.equal(await page.locator(".post-card").count(), 5);
      assert.ok((await page.locator("mark").count()) > 0);
      assert.equal(await page.locator(".post-title img").count(), 0);
      assert.equal(await page.locator(".feed-pagination:not(.compact) .back-top").count(), 1);
      await page.evaluate(() => document.fonts.ready);
      await page.locator(".search-summary").evaluate((element) => Promise.all(element.getAnimations().map((animation) => animation.finished)));
      const navigation = await page.locator(".community-nav").boundingBox();
      const heading = await page.locator(".search-summary").boundingBox();
      measurements.headingGap = Math.round(heading.y - navigation.y - navigation.height);
      await fs.mkdir(new URL("../test-output/search/", import.meta.url), {
        recursive: true,
      });
      await page.waitForTimeout(500);
      await page.screenshot({
        path: new URL(
          "../test-output/search/desktop.png",
          import.meta.url,
        ).pathname.replace(/^\/(\w:)/, "$1"),
        fullPage: true,
      });
      assert.ok(measurements.headingGap < 40, JSON.stringify({navigation, heading}));
      await page.getByRole("button", { name: "技术", exact: true }).click();
      await page.waitForURL(/category=TECH/);
      await poll(
        async () =>
          (await page.locator(".search-results").getAttribute("aria-busy")) ===
          "false",
      );
      assert.equal(
        await page.locator(".category-filters .selected").count(),
        1,
      );
      await page
        .locator(".post-card")
        .first()
        .click({ position: { x: 20, y: 20 } });
      await page.waitForURL(/\/posts\//);
      await page.locator(".detail-card").waitFor();
      assert.equal(await page.locator(".aurora").count(), 0);
      await poll(() => page.evaluate(() => window.__auroraCanvas.getContext("webgl2").isContextLost()));
      await page.getByRole("link", { name: "返回搜索", exact: true }).click();
      await page.waitForURL(/\/search/);
      assert.ok(page.url().includes("category=TECH"));
      await page.locator(".nav-search").hover();
      const input = page.locator(".nav-search input");
      await input.fill("无结果" + keyword);
      await page.mouse.move(0, 0);
      assert.equal(await input.evaluate((element) => element === document.activeElement), true);
      assert.equal(await page.locator(".nav-search").evaluate((element) => element.classList.contains("expanded")), true);
      await input.evaluate((element) => element.blur());
      await poll(async () => !(await page.locator(".nav-search").evaluate((element) => element.classList.contains("expanded"))));
      await page.locator(".nav-search").hover();
      await input.focus();
      await input.evaluate((element) => element.blur());
      assert.equal(await page.locator(".nav-search").evaluate((element) => element.classList.contains("expanded")), true);
      assert.equal(await page.evaluate(() => getComputedStyle(document.documentElement).scrollbarWidth), "none");
      await input.focus();
      await input.press("Enter");
      await page.waitForURL(/q=/);
      await page.locator(".search-empty").waitFor();
      assert.equal(await input.inputValue(), "无结果" + keyword);
      await redis.del("meng:content:limit:search:" + users[0]);
      await page.goto(origin + "/search?q=" + keyword);
      await page.locator(".post-card").first().waitFor();
      measurements.frames = await page.evaluate(async () => {
        const deltas = [];
        let last = performance.now();
        for (let i = 0; i < 90; i++) {
          await new Promise(requestAnimationFrame);
          const now = performance.now();
          deltas.push(now - last);
          last = now;
          window.scrollBy(0, 4);
        }
        return {
          frames: deltas.length,
          over50ms: deltas.filter((t) => t > 50).length,
          max: Math.round(Math.max(...deltas)),
        };
      });
      await page.setViewportSize({ width: 390, height: 844 });
      await poll(() => page.locator(".aurora canvas").evaluate((canvas) => Math.max(canvas.width, canvas.height) <= 512));
      assert.ok(await page.locator(".aurora").evaluate((element) => {
        const bounds = element.getBoundingClientRect();
        return bounds.top === 0 && bounds.height === innerHeight;
      }));
      await page.screenshot({
        path: new URL(
          "../test-output/search/mobile.png",
          import.meta.url,
        ).pathname.replace(/^\/(\w:)/, "$1"),
        fullPage: true,
      });
      assert.ok(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
      );
      await page.getByRole("button", { name: "最新", exact: true }).click();
      await page.getByRole("button", { name: "热门", exact: true }).click();
      await poll(
        async () =>
          (await page.locator(".search-results").getAttribute("aria-busy")) ===
          "false",
      );
      assert.ok(page.url().includes("sort=HOT"));
      assert.equal(await page.locator(".el-message--error").count(), 0);
      await page.emulateMedia({ reducedMotion: "reduce" });
      await wait(100);
      const staticDraws = await page.evaluate(() => window.__auroraDraws);
      await wait(300);
      assert.equal(await page.evaluate(() => window.__auroraDraws), staticDraws);
      assert.ok(
        await page
          .locator(".search-results")
          .evaluate(
            (e) => parseFloat(getComputedStyle(e).transitionDuration) < 0.001,
          ),
      );
      assert.deepEqual(errors, []);
      await page.locator(".aurora canvas").evaluate((canvas) => canvas.getContext("webgl2").getExtension("WEBGL_lose_context").loseContext());
      await page.locator(".aurora.ready").waitFor({state:"hidden"});
      assert.notEqual(await page.locator(".aurora").evaluate((element) => getComputedStyle(element).backgroundImage), "none");
      await page.getByRole("button", { name: "最新", exact: true }).click();
      await poll(async () => (await page.locator(".search-results").getAttribute("aria-busy")) === "false");
      assert.deepEqual(errors, []);
    },
  );
  console.log(
    JSON.stringify({ passed: true, checks: checks.length, measurements }),
  );
  await fs.mkdir(new URL("../test-output/search/", import.meta.url), {
    recursive: true,
  });
  await fs.writeFile(
    new URL("../test-output/search/result.json", import.meta.url),
    JSON.stringify({ passed: true, checks, measurements }, null, 2),
  );
} finally {
  await browser?.close();
  if (contentDb && postIds.length) {
    for (const id of postIds)
      await api("DELETE", "/content/posts/" + id).catch(() => {});
    await wait(800);
    await contentDb.query("DELETE FROM post_tag WHERE post_id IN (?)", [
      postIds,
    ]);
    await contentDb.query("DELETE FROM post_like WHERE post_id IN (?)", [
      postIds,
    ]);
    await contentDb.query("DELETE FROM post_favorite WHERE post_id IN (?)", [
      postIds,
    ]);
    await contentDb.query("DELETE FROM post WHERE id IN (?)", [postIds]);
    await contentDb.execute("DELETE FROM tag WHERE name_key=?", [
      "tag" + keyword,
    ]);
    await cleanupPostReadModels(redis, postIds);
  }
  if (contentDb && users.length) await contentDb.query("DELETE FROM author_daily_stat WHERE author_id IN (?)", [users]);
  await contentDb?.end();
  await wait(300);
  if (growthDb && users.length)
    for (const table of ["exp_record", "check_in", "growth_account"])
      await growthDb.query("DELETE FROM " + table + " WHERE user_id IN (?)", [
        users,
      ]);
  await growthDb?.end();
  if (redis.isOpen) {
    for (const cookie of cookies) {
      const claims = JSON.parse(
        Buffer.from(cookie.split("=")[1].split(".")[1], "base64url"),
      );
      await redis.del("meng:session:" + claims.sessionId);
    }
    for await (const batch of redis.scanIterator({
      MATCH: "meng:*",
      COUNT: 100,
    }))
      for (const key of batch)
        if (users.some((u) => key.endsWith(":" + u))) await redis.del(key);
    await redis.quit();
  }
  if (userDb && users.length)
    await userDb.query("DELETE FROM user WHERE id IN (?)", [users]);
  await userDb?.end();
}
