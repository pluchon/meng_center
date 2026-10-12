import assert from "node:assert/strict";
import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { performance } from "node:perf_hooks";
import mysql from "mysql2/promise";
import { createClient } from "redis";
import { chromium } from "playwright";
import { cleanupPostReadModels } from "./read-model-cleanup.mjs";

const production = process.env.MENG_CREATOR_PRODUCTION === "1";
const origin = production ? "http://127.0.0.1:5187" : "http://127.0.0.1:5173";
const apiOrigin = "http://127.0.0.1:5173";
const base = "http://127.0.0.1:8080";
const nonce = crypto.randomBytes(5).toString("hex");
const users = [],
  cookies = [],
  posts = [],
  checks = [],
  tickets = [];
const measurements = {};
const errors = [];
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const today = new Intl.DateTimeFormat("en-CA", {
  timeZone: "Asia/Taipei",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
}).format(new Date());
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
let userDb, contentDb, growthDb, browser, page, preview;
async function api(method, url, body, account = 0) {
  const response = await fetch(base + url, {
    method,
    headers: {
      Origin: apiOrigin,
      ...(cookies[account] ? { Cookie: cookies[account] } : {}),
      ...(body && !(body instanceof FormData)
        ? { "Content-Type": "application/json" }
        : {}),
    },
    body:
      body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
  });
  return { status: response.status, ...(await response.json()) };
}
function ok(response, status = 200) {
  assert.equal(response.status, status, JSON.stringify(response));
  return response.data;
}
async function check(name, run) {
  await run();
  checks.push(name);
  console.log("PASS " + name);
}
const overview = async (account = 0) =>
  ok(await api("GET", "/content/creator/overview", null, account));
const trend = async () => ok(await api("GET", "/content/creator/trend"));
const daily = async () => (await trend()).at(-1);
const payload = (title) => ({
  title,
  category: "DAILY",
  content: "记录今天的灵感，分享一份小小的快乐。",
  imageIds: [],
  tags: [],
});
async function create(title, account = 0, extra = {}) {
  await redis.del("meng:content:limit:post-interval:" + users[account]);
  const id = ok(
    await api(
      "POST",
      "/content/posts",
      { ...payload(title), ...extra },
      account,
    ),
  );
  posts.push(id);
  return id;
}
async function draft(body) {
  await redis.del("meng:content:limit:draft-interval:" + users[0]);
  return ok(await api("POST", "/content/drafts", body));
}
async function publishedRows(count) {
  await page.waitForFunction(
    (expected) =>
      document.querySelectorAll(".creator-entry").length === expected &&
      document.querySelector(".creator-list")?.getAttribute("aria-busy") ===
        "false",
    count,
  );
}
try {
  if (production) {
    const front = fileURLToPath(
      new URL("../../vue_front/front_user/", import.meta.url),
    );
    await fs.access(path.join(front, "dist/index.html"));
    if (
      await fetch(origin, { signal: AbortSignal.timeout(500) }).catch(
        () => null,
      )
    )
      throw Error("Verification preview port 5187 is already in use.");
    let output = "";
    preview = spawn(
      process.execPath,
      [
        path.join(front, "node_modules/vite/bin/vite.js"),
        "preview",
        "--host",
        "127.0.0.1",
        "--port",
        "5187",
        "--strictPort",
      ],
      {
        cwd: front,
        windowsHide: true,
        env: { ...process.env, VITE_GATEWAY_TARGET: base },
        stdio: ["ignore", "pipe", "pipe"],
      },
    );
    preview.stdout.on("data", (chunk) => {
      output = (output + chunk).slice(-8000);
    });
    preview.stderr.on("data", (chunk) => {
      output = (output + chunk).slice(-8000);
    });
    for (let i = 0; i < 40; i++) {
      if (preview.exitCode !== null)
        throw Error("Production preview exited: " + output);
      if (
        await fetch(origin, { signal: AbortSignal.timeout(500) }).catch(
          () => null,
        )
      )
        break;
      await wait(100);
      if (i === 39) throw Error("Production preview did not become ready.");
    }
  }
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
    const username = `verify_creator_${nonce}_${i}`;
    const [insert] = await userDb.execute(
      "INSERT INTO user(username,password_hash,role,status,level) VALUES(?,?,'USER','NORMAL',1)",
      [username, admin.password_hash],
    );
    users.push(insert.insertId);
    const ticket = crypto.randomUUID();
    tickets.push(ticket);
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
      headers: { Origin: apiOrigin, "Content-Type": "application/json" },
      body: JSON.stringify({
        username,
        password: process.env.MENG_ADMIN_PASSWORD,
        ticket,
      }),
    });
    assert.equal(response.status, 200);
    cookies.push(response.headers.get("set-cookie").split(";")[0]);
  }
  await check("Authentication, empty states and query boundaries", async () => {
    for (const url of [
      "/content/creator/overview",
      "/content/creator/trend",
      "/content/creator/top-posts",
      "/content/posts/mine",
    ])
      ok(await api("GET", url, null, 3), 401);
    assert.deepEqual(await overview(), {
      postCount: 0,
      viewCount: 0,
      likeCount: 0,
      commentCount: 0,
      favoriteCount: 0,
    });
    assert.equal(ok(await api("GET", "/content/posts/mine")).records.length, 0);
    assert.deepEqual(ok(await api("GET", "/content/creator/top-posts")), []);
    for (const extra of ["days=8", "days=0", "days=31", "days=x"])
      ok(await api("GET", "/content/creator/trend?" + extra), 400);
    for (const extra of ["page=0", "size=51"])
      ok(await api("GET", "/content/posts/mine?" + extra), 400);
    for (const days of [7, 30]) {
      const rows = ok(await api("GET", "/content/creator/trend?days=" + days));
      assert.equal(rows.length, days);
      assert.equal(rows.at(-1).date, today);
      rows.forEach((row, i) => {
        assert.equal(new Date(row.date) - new Date(rows[0].date), i * 86400000);
        for (const key of [
          "viewCount",
          "likeCount",
          "commentCount",
          "favoriteCount",
          "postCount",
        ])
          assert.equal(row[key], 0);
      });
    }
  });
  browser = await chromium.launch({ channel: "msedge", headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
  });
  // The isolated production preview is not a new business CORS origin.
  if (production)
    await context.route("**/api/**", async (route) => {
      const response = await route.fetch({
        headers: { ...route.request().headers(), origin: apiOrigin },
      });
      await route.fulfill({ response });
    });
  const cookieParts = cookies[0].split("=");
  await context.addCookies([
    {
      name: cookieParts[0],
      value: cookieParts.slice(1).join("="),
      url: origin,
      httpOnly: true,
    },
  ]);
  page = await context.newPage();
  const chartRequests = [];
  page.on("pageerror", (e) => errors.push(e.stack));
  page.on("request", (request) => {
    if (/echarts|zrender|chartRuntime|creator-charts/.test(request.url()))
      chartRequests.push(request.url());
  });
  await check(
    "Home lazily loads creator charts and empty creator has no axes or footer",
    async () => {
      await page.goto(origin + "/");
      await page.locator(".community-nav").waitFor();
      const homeNavigationWidth = (
        await page.locator(".community-nav").boundingBox()
      ).width;
      assert.equal(chartRequests.length, 0);
      await page.getByRole("link", { name: "创作", exact: true }).click();
      await page
        .getByText("草稿箱还空空的")
        .or(page.getByText("还没有发布过帖子，写下第一份分享吧～"))
        .waitFor();
      assert.equal(
        (await page.locator(".community-nav").boundingBox()).width,
        homeNavigationWidth,
      );
      assert.equal(await page.locator(".chart-host").count(), 0);
      assert.equal(await page.locator(".community-footer").count(), 0);
      assert.equal(
        await page.locator(".community-nav a.selected").innerText(),
        "创作",
      );
    },
  );
  const imageBytes = await fs.readFile(
    new URL(
      "../../vue_front/front_user/src/assets/user_avatar.png",
      import.meta.url,
    ),
  );
  const imageIds = [];
  for (let i = 0; i < 7; i++) {
    const form = new FormData();
    form.append(
      "file",
      new Blob([imageBytes], { type: "image/png" }),
      "creator.png",
    );
    imageIds.push(ok(await api("POST", "/content/images", form)).id);
  }
  const richDraft = await draft({
    title: "草稿配图 " + nonce,
    content: "五张缩略图和两张额外配图",
    category: "ANIME",
    imageIds,
  });
  await draft({ title: "", content: "没有标题也可以继续记录", imageIds: [] });
  await draft({ title: "只有标题", content: "", imageIds: [] });
  await check(
    "Draft summaries retain category, original image order and five-image limit",
    async () => {
      const rows = ok(await api("GET", "/content/drafts"));
      assert.equal(rows.length, 3);
      const rich = rows.find((d) => d.id === richDraft);
      assert.equal(rich.category, "ANIME");
      assert.equal(rich.imageCount, 7);
      assert.deepEqual(
        rich.images.map((i) => i.id),
        imageIds.slice(0, 5),
      );
      const empty = rows.find((d) => !d.title);
      assert.equal(empty.category ?? null, null);
      assert.deepEqual(empty.images, []);
      assert.deepEqual(ok(await api("GET", "/content/drafts", null, 1)), []);
      ok(await api("GET", "/content/drafts/" + richDraft, null, 1), 403);
    },
  );
  const postImages = [];
  for (let i = 0; i < 2; i++) {
    const form = new FormData();
    form.append(
      "file",
      new Blob([imageBytes], { type: "image/png" }),
      "post.png",
    );
    postImages.push(ok(await api("POST", "/content/images", form)).id);
  }
  for (let i = 0; i < 12; i++)
    await create(
      "创作测试 " + nonce + " " + i,
      0,
      i === 0
        ? { imageIds: [postImages[0]] }
        : i === 1
          ? { coverImageId: postImages[1] }
          : {},
    );
  const target = posts[0];
  await create("他人的帖子 " + nonce, 1);
  await check(
    "Published pagination, account isolation, cover fallback and all-time ranking",
    async () => {
      const first = ok(await api("GET", "/content/posts/mine"));
      const second = ok(await api("GET", "/content/posts/mine?page=2"));
      assert.equal(first.total, 12);
      assert.equal(first.records.length, 10);
      assert.equal(second.records.length, 2);
      assert.equal(
        second.records.find((p) => p.id === target).cover.id,
        postImages[0],
      );
      assert.equal(
        second.records.find((p) => p.id === posts[1]).cover.id,
        postImages[1],
      );
      const other = ok(await api("GET", "/content/posts/mine", null, 1));
      assert.equal(other.total, 1);
      assert.equal(other.records[0].id, posts.at(-1));
      assert.equal((await overview()).postCount, 12);
      await contentDb.execute(
        "UPDATE post SET create_time=UTC_TIMESTAMP()-INTERVAL 15 DAY,hot_score=10000 WHERE id=?",
        [target],
      );
      const top = ok(await api("GET", "/content/creator/top-posts"));
      assert.equal(top.length, 5);
      assert.equal(top[0].id, target);
      assert.ok(
        top.every((p, i) => i === 0 || p.hotScore <= top[i - 1].hotScore),
      );
      assert.equal((await daily()).postCount, 11);
    },
  );
  await check(
    "Views, likes and favorites are transactional and duplicate-safe",
    async () => {
      await Promise.all(
        Array.from({ length: 8 }, () =>
          api("GET", "/content/posts/" + target, null, 1).then((r) => ok(r)),
        ),
      );
      assert.equal((await daily()).viewCount, 1);
      ok(await api("GET", "/content/posts/" + target));
      assert.equal((await daily()).viewCount, 2);
      for (const kind of ["like", "favorite"]) {
        await Promise.all(
          Array.from({ length: 8 }, () =>
            api("PUT", `/content/posts/${target}/${kind}`, null, 1).then((r) =>
              ok(r),
            ),
          ),
        );
        assert.equal((await daily())[kind + "Count"], 1);
        ok(await api("DELETE", `/content/posts/${target}/${kind}`, null, 1));
        ok(await api("DELETE", `/content/posts/${target}/${kind}`, null, 1));
        assert.equal((await daily())[kind + "Count"], 0);
      }
      const total = await overview();
      assert.equal(total.viewCount, 2);
      assert.equal(total.likeCount, 0);
      assert.equal(total.favoriteCount, 0);
    },
  );
  await check(
    "Cancelling prior-day interactions and deleting comments preserves daily history",
    async () => {
      for (const kind of ["like", "favorite"]) {
        ok(await api("PUT", `/content/posts/${target}/${kind}`, null, 1));
        await contentDb.execute(
          `UPDATE post_${kind} SET create_time=UTC_TIMESTAMP()-INTERVAL 1 DAY WHERE post_id=? AND user_id=?`,
          [target, users[1]],
        );
      }
      // Move fixture interactions into yesterday to emulate a prior-day relationship.
      await contentDb.execute(
        "UPDATE author_daily_stat SET like_count=0,favorite_count=0 WHERE author_id=? AND stat_date=?",
        [users[0], today],
      );
      await contentDb.execute(
        "INSERT INTO author_daily_stat(author_id,stat_date,like_count,favorite_count) VALUES(?,DATE_SUB(?,INTERVAL 1 DAY),1,1)",
        [users[0], today],
      );
      for (const kind of ["like", "favorite"])
        ok(await api("DELETE", `/content/posts/${target}/${kind}`, null, 1));
      const rows = await trend();
      assert.equal(rows.at(-1).likeCount, 0);
      assert.equal(rows.at(-2).likeCount, 1);
      assert.equal(rows.at(-2).favoriteCount, 1);
      const comment = ok(
        await api(
          "POST",
          `/content/posts/${target}/comments`,
          { content: "评论统计验证", imageIds: [] },
          1,
        ),
      );
      assert.equal((await daily()).commentCount, 1);
      ok(await api("DELETE", "/content/comments/" + comment.id, null, 1));
      assert.equal((await daily()).commentCount, 1);
      assert.equal((await overview()).commentCount, 0);
    },
  );
  await check(
    "Desktop charts, pagination, detail return, draft hydration and confirmations",
    async () => {
      await page.goto(origin + "/creator");
      await publishedRows(10);
      await page.locator(".chart-host svg").first().waitFor();
      assert.ok(chartRequests.length > 0);
      await page.locator(".shape-waves").waitFor();
      const webGpuAvailable = await page.evaluate(async () =>
        Boolean(await navigator.gpu?.requestAdapter()),
      );
      if (webGpuAvailable) {
        await page.locator(".shape-waves.ready").waitFor();
        const resolution = await page.locator(".shape-waves canvas").evaluate((canvas) => ({
          actual: [canvas.width, canvas.height],
          expected: [
            Math.round(canvas.clientWidth * Math.min(devicePixelRatio, 2)),
            Math.round(canvas.clientHeight * Math.min(devicePixelRatio, 2)),
          ],
        }));
        assert.deepEqual(resolution.actual, resolution.expected);
      }
      const existingChart = await page
        .locator(".trend-panel .chart-host")
        .elementHandle();
      assert.equal(
        Math.round((await page.locator(".creator-page").boundingBox()).width),
        1080,
      );
      assert.equal(
        await page
          .locator("html")
          .evaluate((e) => getComputedStyle(e).scrollbarWidth),
        "none",
      );
      await page.getByRole("button", { name: "近 30 天", exact: true }).click();
      await page.locator(".chart-host svg").first().waitFor();
      assert.ok(await existingChart.evaluate((node) => node.isConnected));
      assert.equal(await page.locator(".trend-range.long-range").count(), 1);
      assert.equal(
        await page
          .getByRole("button", { name: "近 30 天", exact: true })
          .getAttribute("aria-pressed"),
        "true",
      );
      const legend = page.locator(".chart-legend button").first();
      await legend.click();
      assert.equal(await legend.getAttribute("aria-pressed"), "false");
      const plot = await page.locator(".chart-host").first().boundingBox();
      await page.mouse.move(plot.x + plot.width - 20, plot.y + 80);
      await page.waitForFunction(() => {
        const tooltip =
          document.querySelector(".chart-host svg")?.textContent ?? "";
        return ["浏览:", "喜欢:", "评论:", "收藏:"].every((label) =>
          tooltip.includes(label),
        );
      });
      await legend.click();
      await page
        .locator(".feed-pagination button")
        .filter({ hasText: /^2$/ })
        .click();
      await publishedRows(2);
      await page
        .locator(".creator-entry")
        .last()
        .click({ position: { x: 800, y: 80 } });
      await page
        .getByRole("link", { name: "返回创作中心", exact: true })
        .first()
        .click();
      await publishedRows(2);
      assert.ok(page.url().includes("page=2"));
      await page.getByRole("tab", { name: /草稿箱/ }).click();
      await publishedRows(3);
      assert.equal(
        await page.getByText("还没起标题", { exact: true }).count(),
        1,
      );
      assert.equal(
        await page.getByText("还没写正文", { exact: true }).count(),
        1,
      );
      assert.equal(await page.locator(".draft-images .el-image").count(), 5);
      assert.equal(await page.getByText("+2", { exact: true }).count(), 1);
      const continueLink = page
        .locator(".creator-entry")
        .filter({ hasText: "草稿配图" })
        .getByRole("link", { name: "继续写" });
      assert.equal(
        await continueLink.getAttribute("href"),
        "/posts/new?draft=" + richDraft,
      );
      await continueLink.click();
      await page.waitForURL("**/posts/new?draft=*");
      await page.waitForFunction(() =>
        document.querySelector("#post-title")?.value.startsWith("草稿配图"),
      );
      assert.equal(
        await page.locator("#post-title").inputValue(),
        "草稿配图 " + nonce,
      );
      await page.goto(origin + "/creator?tab=drafts");
      await publishedRows(3);
      await page.locator(".delete-entry").first().click();
      await page.getByRole("button", { name: "再想想", exact: true }).click();
      await page.locator(".el-message-box").waitFor({ state: "hidden" });
      await publishedRows(3);
      await page.locator(".delete-entry").first().click();
      const draftDeleted = page.waitForResponse(
        (response) =>
          response.url().includes("/api/content/drafts/") &&
          response.request().method() === "DELETE",
      );
      await page
        .locator(".el-message-box")
        .getByRole("button", { name: "删除", exact: true })
        .click();
      const deleteResponse = await draftDeleted;
      assert.equal(
        deleteResponse.status(),
        200,
        JSON.stringify({
          body: await deleteResponse.text(),
          origin: (await deleteResponse.request().allHeaders()).origin,
        }),
      );
      await publishedRows(2);
      assert.equal(ok(await api("GET", "/content/drafts")).length, 2);
      await page.getByRole("tab", { name: /已发布/ }).click();
      await publishedRows(10);
      await page
        .locator(".feed-pagination button")
        .filter({ hasText: /^2$/ })
        .click();
      await publishedRows(2);
      for (let i = 0; i < 2; i++) {
        await page.locator(".delete-entry").first().click();
        await page
          .locator(".el-message-box")
          .getByRole("button", { name: "删除", exact: true })
          .click();
        await publishedRows(i === 0 ? 1 : 10);
      }
      assert.ok(page.url().includes("page=1"));
      assert.equal((await overview()).postCount, 10);
      assert.equal((await overview()).viewCount, 0);
      assert.equal((await daily()).viewCount, 2);
      assert.ok(
        !ok(await api("GET", "/content/creator/top-posts")).some(
          (p) => p.id === target,
        ),
      );
    },
  );
  await check(
    "Independent retry, mobile overflow, reduced motion and navigation cleanup",
    async () => {
      let fail = true;
      await page.route("**/api/content/creator/overview", (route) =>
        fail
          ? route.fulfill({
              status: 503,
              contentType: "application/json",
              body: JSON.stringify({
                code: "SERVICE_UNAVAILABLE",
                message: "temporary fixture failure",
              }),
            })
          : route.continue(),
      );
      await page.reload();
      await publishedRows(10);
      await page.locator(".creator-charts svg").first().waitFor();
      assert.equal(await page.locator(".creator-metrics").count(), 0);
      fail = false;
      await page.getByRole("button", { name: /重试/ }).first().click();
      await page.locator(".creator-metrics").waitFor();
      await page.setViewportSize({ width: 390, height: 844 });
      await page.emulateMedia({ reducedMotion: "reduce" });
      await wait(250);
      assert.ok(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
      );
      assert.equal(
        await page
          .locator(".creator-cheer")
          .evaluate((e) => getComputedStyle(e).animationName),
        "none",
      );
      await page.getByRole("tab", { name: /草稿箱/ }).click();
      await publishedRows(2);
      assert.ok(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
      );
      await page.setViewportSize({ width: 1440, height: 1000 });
      measurements.scroll = await page.evaluate(
        () =>
          new Promise((resolve) => {
            const samples = [];
            let previous;
            function frame(t) {
              if (previous) samples.push(t - previous);
              previous = t;
              window.scrollBy(0, 4);
              if (samples.length < 90) requestAnimationFrame(frame);
              else
                resolve({
                  frames: samples.length,
                  over50ms: samples.filter((v) => v > 50).length,
                  p95ms: samples.sort((a, b) => a - b)[
                    Math.floor(samples.length * 0.95)
                  ],
                });
            }
            requestAnimationFrame(frame);
          }),
      );
      await page.getByRole("link", { name: "首页", exact: true }).click();
      await page.waitForURL(origin + "/");
      await page
        .locator("html.creator-scrollbar-hidden")
        .waitFor({ state: "detached" });
      assert.equal(await page.locator(".shape-waves").count(), 0);
      assert.deepEqual(errors, []);
    },
  );
  await check("Concurrent creator reads remain correct", async () => {
    const times = [];
    await Promise.all(
      Array.from({ length: 24 }, async (_, i) => {
        const start = performance.now();
        const result = ok(
          await api(
            "GET",
            i % 2 ? "/content/creator/trend?days=30" : "/content/posts/mine",
          ),
        );
        assert.ok(i % 2 ? result.length === 30 : result.total === 10);
        times.push(performance.now() - start);
      }),
    );
    times.sort((a, b) => a - b);
    measurements.reads = {
      concurrency: 24,
      p50ms: times[12],
      p95ms: times[22],
      maxms: times.at(-1),
    };
  });
  await check(
    "Concurrent new views on separate posts do not lose author increments",
    async () => {
      const before = (await daily()).viewCount;
      const entries = ok(await api("GET", "/content/posts/mine")).records.slice(
        0,
        8,
      );
      await Promise.all(
        entries.map((post) =>
          api("GET", "/content/posts/" + post.id, null, 1).then((r) => ok(r)),
        ),
      );
      assert.equal((await daily()).viewCount, before + 8);
      assert.equal((await overview()).viewCount, 8);
    },
  );
  console.log(
    JSON.stringify({ passed: true, checks: checks.length, measurements }),
  );
} catch (failure) {
  if (page) console.error("UI failure at " + page.url());
  if (errors.length) console.error(errors);
  throw failure;
} finally {
  await browser?.close();
  if (preview && preview.exitCode === null) {
    const stopped = new Promise((resolve) => preview.once("exit", resolve));
    preview.kill();
    await stopped;
  }
  if (contentDb && users.length) {
    const [files] = await contentDb.query(
      "SELECT file_name,thumb_name FROM post_image WHERE uploader_id IN (?)",
      [users],
    );
    const [comments] = await contentDb.query(
      "SELECT id FROM comment WHERE author_id IN (?)",
      [users],
    );
    if (comments.length)
      await contentDb.query(
        "DELETE FROM comment_like WHERE comment_id IN (?)",
        [comments.map((c) => c.id)],
      );
    await contentDb.query("DELETE FROM comment WHERE author_id IN (?)", [
      users,
    ]);
    if (posts.length) {
      for (const table of ["post_tag", "post_like", "post_favorite"])
        await contentDb.query(
          "DELETE FROM " + table + " WHERE post_id IN (?)",
          [posts],
        );
      await contentDb.query("DELETE FROM post WHERE id IN (?)", [posts]);
      await cleanupPostReadModels(redis, posts);
    }
    for (const table of [
      "post_image",
      "post_draft",
      "post_draft_owner",
      "author_daily_stat",
    ])
      await contentDb.query(
        "DELETE FROM " +
          table +
          " WHERE " +
          (table === "post_image" ? "uploader_id" : "author_id") +
          " IN (?)",
        [users],
      );
    for (const image of files)
      for (const file of [image.file_name, image.thumb_name])
        await fs
          .unlink(path.join(process.env.MENG_CONTENT_IMAGE_ROOT, file))
          .catch(() => {});
  }
  await contentDb?.end();
  await wait(800);
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
    for (const ticket of tickets)
      await redis.del("meng:captcha-ticket:" + ticket);
    for await (const batch of redis.scanIterator({
      MATCH: "meng:*",
      COUNT: 100,
    }))
      for (const key of batch)
        if (
          users.some((u) => key.endsWith(":" + u)) ||
          posts.some((p) => key.includes(":viewed:" + p + ":"))
        )
          await redis.del(key);
    await redis.quit();
  }
  if (userDb && users.length)
    await userDb.query("DELETE FROM user WHERE id IN (?)", [users]);
  await userDb?.end();
}
