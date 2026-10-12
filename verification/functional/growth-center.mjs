import assert from "node:assert/strict";
import crypto from "node:crypto";
import mysql from "mysql2/promise";
import { createClient } from "redis";
import { chromium } from "playwright";
import { performance } from "node:perf_hooks";
import { spawn } from "node:child_process";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const base = "http://127.0.0.1:8080";
const production = process.env.MENG_GROWTH_PRODUCTION === "1";
const origin = production ? "http://127.0.0.1:5188" : "http://127.0.0.1:5173";
const apiOrigin = "http://127.0.0.1:5173";
const nonce = crypto.randomBytes(5).toString("hex");
const users = [],
  cookies = [],
  tickets = [];
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
let userDb, growthDb, browser, preview;
async function api(url, account = 0, method = "GET") {
  const response = await fetch(base + url, {
    method,
    headers: {
      Origin: apiOrigin,
      ...(cookies[account] ? { Cookie: cookies[account] } : {}),
    },
  });
  return { status: response.status, ...(await response.json()) };
}
function ok(response, status = 200) {
  assert.equal(response.status, status, JSON.stringify(response));
  return response.data;
}
async function check(name, run) {
  await run();
  console.log("PASS " + name);
}
const shifted = (today, n) => {
  const value = new Date(today + "T00:00:00Z");
  value.setUTCDate(value.getUTCDate() + n);
  return value.toISOString().slice(0, 10);
};
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
      throw Error("Verification port 5188 is already in use");
    preview = spawn(
      process.execPath,
      [
        path.join(front, "node_modules/vite/bin/vite.js"),
        "preview",
        "--host",
        "127.0.0.1",
        "--port",
        "5188",
        "--strictPort",
      ],
      {
        cwd: front,
        windowsHide: true,
        env: { ...process.env, VITE_GATEWAY_TARGET: base },
        stdio: "ignore",
      },
    );
    for (let i = 0; i < 50; i++) {
      if (preview.exitCode !== null) throw Error("Production preview exited");
      if (
        await fetch(origin, { signal: AbortSignal.timeout(500) }).catch(
          () => null,
        )
      )
        break;
      await new Promise((resolve) => setTimeout(resolve, 100));
      if (i === 49) throw Error("Preview failed to start");
    }
  }
  await redis.connect();
  userDb = await connect("user");
  growthDb = await connect("growth");
  const [[admin]] = await userDb.execute(
    "SELECT password_hash FROM user WHERE username=?",
    [process.env.MENG_ADMIN_USERNAME],
  );
  assert.ok(admin);
  for (let i = 0; i < 2; i++) {
    const username = `verify_growth_${nonce}_${i}`;
    const [row] = await userDb.execute(
      "INSERT INTO user(username,password_hash,role,status,level) VALUES(?,?,'USER','NORMAL',1)",
      [username, admin.password_hash],
    );
    users.push(row.insertId);
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
  const overview = ok(await api("/growth/overview"));
  const today = overview.today,
    month = today.slice(0, 7);
  const rewards = Object.fromEntries(
    overview.tasks.map((task) => [task.source, task.reward]),
  );
  for (const n of [-1, -2, -3, -10])
    await growthDb.execute(
      "INSERT INTO check_in(user_id,check_date) VALUES(?,?)",
      [users[0], shifted(today, n)],
    );
  const fixture = [];
  for (let n = -34; n <= 0; n++) {
    if (n === -6 || n === -21) continue;
    const source = n % 2 === 0 ? "POST" : "COMMENT",
      date = shifted(today, n);
    fixture.push({ source, date, amount: rewards[source] });
  }
  for (const n of [-1, -2, -3, -10])
    fixture.push({
      source: "CHECK_IN",
      date: shifted(today, n),
      amount: rewards.CHECK_IN,
    });
  fixture.push(
    { source: "POST", date: today, amount: rewards.POST },
    { source: "POST", date: today, amount: rewards.POST },
    { source: "COMMENT", date: today, amount: rewards.COMMENT },
  );
  for (const [i, row] of fixture.entries())
    await growthDb.execute(
      "INSERT INTO exp_record(user_id,source,source_id,amount,record_date,create_time) VALUES(?,?,?,?,?,?)",
      [
        users[0],
        row.source,
        `verify_${nonce}_${i}`,
        row.amount,
        row.date,
        row.date + " 07:00:00",
      ],
    );
  const exp = fixture.reduce((sum, row) => sum + row.amount, 0),
    level = overview.levels
      .filter((row) => exp >= row.requiredExp)
      .at(-1).level;
  await growthDb.execute(
    "INSERT INTO growth_account(user_id,exp,level) VALUES(?,?,?)",
    [users[0], exp, level],
  );
  await check(
    "Authentication, account isolation and strict query boundaries",
    async () => {
      for (const path of [
        "/growth/check-ins",
        "/growth/overview",
        "/growth/exp-stats",
        "/growth/exp-records",
      ])
        ok(await api(path, 2), 401);
      assert.equal(ok(await api("/growth/exp-records", 1)).total, 0);
      assert.equal(ok(await api("/growth/check-ins", 1)).streak, 0);
      assert.ok(
        ok(await api("/growth/overview", 1)).tasks.every(
          (task) => task.activeDays === 0 && task.earned === 0,
        ),
      );
      const [[count]] = await growthDb.execute(
        "SELECT COUNT(*) total FROM growth_account WHERE user_id=?",
        [users[1]],
      );
      assert.equal(count.total, 0);
      for (const query of ["month=2026-2", "month=2026-13", "month=2099-01"])
        ok(await api("/growth/check-ins?" + query), 400);
      for (const query of ["days=0", "days=8", "days=31", "days=x"])
        ok(await api("/growth/exp-stats?" + query), 400);
      for (const query of ["page=0", "size=0", "size=51", "source=BAD"])
        ok(await api("/growth/exp-records?" + query), 400);
    },
  );
  await check(
    "Calendar streak, task caps and distinct active days",
    async () => {
      const data = ok(await api("/growth/check-ins"));
      assert.equal(data.streak, 3);
      assert.equal(data.month, month);
      assert.equal(data.reward, rewards.CHECK_IN);
      assert.equal(
        data.count,
        [-1, -2, -3, -10]
          .map((n) => shifted(today, n))
          .filter((date) => date.startsWith(month)).length,
      );
      const tasks = ok(await api("/growth/overview")).tasks;
      for (const task of tasks) {
        assert.equal(
          task.count,
          fixture.filter(
            (row) => row.source === task.source && row.date === today,
          ).length,
        );
        assert.equal(task.earned, task.count * rewards[task.source]);
        assert.equal(
          task.activeDays,
          new Set(
            fixture
              .filter((row) => row.source === task.source)
              .map((row) => row.date),
          ).size,
        );
      }
    },
  );
  await check(
    "Zero-filled statistics, shared interval totals and stable source pagination",
    async () => {
      for (const count of [7, 30]) {
        const data = ok(await api("/growth/exp-stats?days=" + count));
        assert.equal(data.days.length, count);
        for (const [i, row] of data.days.entries()) {
          assert.equal(row.date, shifted(today, i - count + 1));
          assert.equal(
            row.amount,
            fixture
              .filter((item) => item.date === row.date)
              .reduce((sum, item) => sum + item.amount, 0),
          );
        }
        assert.equal(
          data.days.reduce((sum, row) => sum + row.amount, 0),
          data.sources.reduce((sum, row) => sum + row.amount, 0),
        );
      }
      const first = ok(await api("/growth/exp-records"));
      assert.equal(first.total, fixture.length);
      assert.equal(first.records.length, 12);
      assert.deepEqual(Object.keys(first.records[0]).sort(), [
        "amount",
        "source",
        "time",
      ]);
      assert.match(first.records[0].time, /15:00:00\+08:00$/);
      for (const source of ["CHECK_IN", "POST", "COMMENT"]) {
        const data = ok(
          await api("/growth/exp-records?source=" + source + "&size=50"),
        );
        assert.equal(
          data.total,
          fixture.filter((row) => row.source === source).length,
        );
        assert.ok(data.records.every((row) => row.source === source));
      }
      assert.equal(
        ok(await api("/growth/exp-records?page=99999999")).records.length,
        0,
      );
    },
  );
  browser = await chromium.launch({ channel: "msedge", headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    deviceScaleFactor: 1,
  });
  // 隔离预览只验证构建产物，沿用开发站点的允许来源，不改业务 CORS 配置。
  if (production)
    await context.route("**/api/**", async (route) => {
      const response = await route.fetch({
        headers: { ...route.request().headers(), origin: apiOrigin },
      });
      await route.fulfill({ response });
    });
  const separator = cookies[0].indexOf("=");
  await context.addCookies([
    {
      name: cookies[0].slice(0, separator),
      value: cookies[0].slice(separator + 1),
      url: origin,
      httpOnly: true,
    },
  ]);
  const page = await context.newPage(),
    errors = [],
    requests = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("request", (request) => requests.push(request.url()));
  await page.goto(origin + "/growth");
  await page.locator(".experience-records li").first().waitFor();
  await page.locator(".calendar-day.now").waitFor();
  await page.evaluate(() => document.fonts.ready);
  await check(
    "Reference layout, hidden scrollbars and lazy chart loading",
    async () => {
      const box = await page.locator(".growth-view").boundingBox();
      assert.ok(Math.abs(box.width - 1320) < 2);
      assert.equal(await page.locator(".community-footer").count(), 0);
      assert.equal(
        await page.evaluate(
          () => getComputedStyle(document.documentElement).scrollbarWidth,
        ),
        "none",
      );
      assert.ok(
        !requests.some((url) =>
          /chartRuntime|creator-charts|echarts/.test(url),
        ),
        "Charts loaded before expansion",
      );
      await page.screenshot({
        path: new URL(
          "../test-output/growth-actual.png",
          import.meta.url,
        ).pathname.replace(/^\/(\w:)/, "$1"),
        fullPage: true,
      });
      assert.deepEqual(errors, []);
    },
  );
  await check("Month cache and records URL filters", async () => {
    await page.getByRole("button", { name: "上个月", exact: true }).click();
    await page
      .getByRole("button", { name: "下个月", exact: true })
      .waitFor({ state: "visible" });
    await page.waitForFunction(
      () =>
        document.querySelector(".month-controls button:last-child")
          ?.disabled === false,
    );
    const count = requests.filter((url) =>
      url.includes("/growth/check-ins?"),
    ).length;
    await page.getByRole("button", { name: "下个月", exact: true }).click();
    await page.waitForFunction(
      () =>
        document.querySelector(".month-controls button:last-child")
          ?.disabled === true,
    );
    await page.getByRole("button", { name: "上个月", exact: true }).click();
    await page.waitForFunction(
      () =>
        document.querySelector(".month-controls button:last-child")
          ?.disabled === false,
    );
    assert.equal(
      requests.filter((url) => url.includes("/growth/check-ins?")).length,
      count,
    );
    await page.getByRole("button", { name: "下个月", exact: true }).click();
    await page.waitForFunction(
      () =>
        document.querySelector(".month-controls button:last-child")
          ?.disabled === true,
    );
    await page
      .locator(".source-filter")
      .getByRole("button", { name: "评论", exact: true })
      .click();
    await page.waitForURL(/source=COMMENT/);
    await page.waitForFunction(() =>
      [...document.querySelectorAll(".experience-records li strong")].every(
        (node) => node.textContent === "评论",
      ),
    );
    await page
      .locator(".source-filter")
      .getByRole("button", { name: "全部", exact: true })
      .click();
    await page.waitForURL((url) => !url.searchParams.has("source"));
  });
  await check(
    "Short and long pages preserve pagination viewport position",
    async () => {
      const footer = page.locator(".records-controls");
      await page.waitForFunction(
        () =>
          document
            .querySelector(".experience-records")
            ?.getAttribute("aria-busy") === "false",
      );
      await footer.scrollIntoViewIfNeeded();
      for (const target of [4, 1, 3, 1]) {
        const before = await footer.boundingBox();
        await footer
          .locator(".feed-pagination button")
          .filter({ hasText: new RegExp("^" + target + "$") })
          .click();
        await page.waitForFunction(
          (value) =>
            document
              .querySelector('.feed-pagination button[aria-current="page"]')
              ?.textContent.trim() === String(value) &&
            document
              .querySelector(".experience-records")
              ?.getAttribute("aria-busy") === "false",
          target,
        );
        const after = await footer.boundingBox();
        assert.ok(
          Math.abs(before.y - after.y) < 3,
          `Page ${target} moved controls by ${after.y - before.y}px`,
        );
      }
    },
  );
  await check(
    "Check-in refreshes calendar, tasks, records and shared navigation",
    async () => {
      // 先访问三十天，再切回七天签到，已缓存的统计也必须刷新。
      await page
        .locator(".record-actions")
        .getByRole("button", { name: "可视化", exact: true })
        .click();
      await page
        .locator(".experience-charts .creator-chart svg")
        .first()
        .waitFor();
      const initialThirty = page.waitForResponse(
        (response) =>
          response.url().includes("exp-stats?days=30") &&
          response.status() === 200,
      );
      await page.getByRole("button", { name: "近 30 天", exact: true }).click();
      await initialThirty;
      await page.getByRole("button", { name: "近 7 天", exact: true }).click();
      await page.getByRole("button", { name: "关闭经验可视化" }).click();
      const signingResponse = page.waitForResponse(
        (response) =>
          response.url().includes("/growth/check-ins") &&
          response.request().method() === "POST",
      );
      await page.locator(".check-in").click();
      const signed = await signingResponse;
      ok({ status: signed.status(), ...(await signed.json()) });
      await page
        .getByRole("button", { name: "今天已签到", exact: true })
        .waitFor();
      await page.waitForFunction(() =>
        document
          .querySelector(".calendar-stickers")
          ?.textContent.includes("连续 4 天"),
      );
      assert.equal(ok(await api("/growth/me")).exp, exp + rewards.CHECK_IN);
      ok(await api("/growth/check-ins", 0, "POST"), 409);
      assert.equal(ok(await api("/growth/check-ins")).streak, 4);
      await page
        .locator(".record-actions")
        .getByRole("button", { name: "可视化", exact: true })
        .click();
      await page
        .locator(".experience-charts .creator-chart svg")
        .first()
        .waitFor();
      assert.equal(
        await page.locator(".experience-charts .creator-chart svg").count(),
        2,
      );
      await page.getByRole("button", { name: "近 30 天", exact: true }).click();
      await page
        .locator(".experience-charts .creator-chart svg")
        .nth(1)
        .waitFor();
      await page.getByRole("dialog", { name: "经验可视化" }).waitFor();
      // 截图等待折线入场结束，避免把展开中的裁剪区域当成最终布局。
      await page.waitForTimeout(1200);
      await page.locator(".growth-visualization").screenshot({
        path: new URL(
          "../test-output/growth-charts.png",
          import.meta.url,
        ).pathname.replace(/^\/(\w:)/, "$1"),
      });
      await page.getByRole("button", { name: "近 7 天", exact: true }).click();
      await page.getByRole("button", { name: "近 30 天", exact: true }).click();
      assert.equal(
        requests.filter((url) => url.includes("exp-stats?days=30")).length,
        2,
        "Cached thirty days must refresh once after signing",
      );
      await page.getByRole("button", { name: "关闭经验可视化" }).click();
      await page.locator('.community-nav a[href="/"]').first().click();
      await page.locator(".home-feed").waitFor();
      assert.equal(await page.locator(".home-sidebar").count(), 0);
      assert.equal(ok(await api("/growth/me")).checkedIn, true);
    },
  );
  await check(
    "Mobile layout, reduced motion and independent retry",
    async () => {
      await page.setViewportSize({ width: 390, height: 844 });
      await page.emulateMedia({ reducedMotion: "reduce" });
      let failed = true;
      await page.route("**/api/growth/overview", (route) =>
        failed
          ? route.fulfill({
              status: 503,
              contentType: "application/json",
              body: JSON.stringify({
                code: "SERVICE_UNAVAILABLE",
                message: "Temporary failure",
              }),
            })
          : route.fallback(),
      );
      await page.goto(origin + "/growth");
      await page.locator(".calendar-day.signed").first().waitFor();
      await page.locator(".tasks-panel .page-state").waitFor();
      assert.ok(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth + 1,
        ),
      );
      failed = false;
      await page
        .locator(".tasks-panel")
        .getByRole("button", { name: /重试/ })
        .click();
      await page.locator(".growth-tasks .task").first().waitFor();
      await page
        .locator(".record-actions")
        .getByRole("button", { name: "可视化", exact: true })
        .click();
      await page.getByRole("dialog", { name: "经验可视化" }).waitFor();
      await page.locator(".experience-charts svg").nth(1).waitFor();
      assert.equal(
        await page.locator(".growth-view .experience-charts").count(),
        0,
      );
      assert.ok(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
      );
      await page.keyboard.press("Escape");
      await page
        .getByRole("dialog", { name: "经验可视化" })
        .waitFor({ state: "hidden" });
      assert.equal(
        await page.evaluate(() =>
          document.activeElement?.getAttribute("aria-haspopup"),
        ),
        "dialog",
      );
      await page.screenshot({
        path: new URL(
          "../test-output/growth-mobile.png",
          import.meta.url,
        ).pathname.replace(/^\/(\w:)/, "$1"),
        fullPage: true,
      });
      assert.deepEqual(errors, []);
    },
  );
  await check(
    "Four, five and six week months keep the calendar height fixed",
    async () => {
      let displayed = "2021-02";
      await page.route("**/api/growth/check-ins", async (route) => {
        const response = await route.fetch();
        const body = await response.json();
        body.data = { ...body.data, month: displayed, dates: [], count: 0 };
        await route.fulfill({ response, json: body });
      });
      for (const width of [1440, 390]) {
        await page.setViewportSize({ width, height: 900 });
        const heights = [];
        for (const month of ["2021-02", "2026-09", "2026-08"]) {
          displayed = month;
          await page.goto(origin + "/growth");
          await page.waitForFunction(
            (expected) =>
              document
                .querySelector(".month-controls")
                ?.textContent.includes(expected),
            month.slice(0, 4),
          );
          await page.locator(".calendar-day").first().waitFor();
          heights.push(
            await page
              .locator(".calendar-panel")
              .evaluate((el) => el.clientHeight),
          );
        }
        assert.ok(
          Math.max(...heights) - Math.min(...heights) < 2,
          JSON.stringify({ width, heights }),
        );
      }
      await page.unroute("**/api/growth/check-ins");
    },
  );
  await check("24 concurrent read requests remain consistent", async () => {
    const paths = [
      "/growth/overview",
      "/growth/check-ins",
      "/growth/exp-stats?days=30",
      "/growth/exp-records?page=2",
    ];
    const latency = await Promise.all(
      Array.from({ length: 24 }, async (_, index) => {
        const start = performance.now();
        ok(await api(paths[index % paths.length]));
        return performance.now() - start;
      }),
    );
    latency.sort((a, b) => a - b);
    console.log(
      "Read latency p50=" +
        Math.round(latency[12]) +
        "ms p95=" +
        Math.round(latency[22]) +
        "ms",
    );
  });
} finally {
  await browser?.close();
  if (preview && preview.exitCode === null) {
    const exited = new Promise((resolve) => preview.once("exit", resolve));
    preview.kill();
    await exited;
  }
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
      await redis.sRem("meng:user-sessions:" + claims.userId, claims.sessionId);
    }
    for (const ticket of tickets)
      await redis.del("meng:captcha-ticket:" + ticket);
    for (const id of users) await redis.del("meng:user-sessions:" + id);
    await redis.quit();
  }
  if (userDb && users.length)
    await userDb.query("DELETE FROM user WHERE id IN (?)", [users]);
  await userDb?.end();
}
