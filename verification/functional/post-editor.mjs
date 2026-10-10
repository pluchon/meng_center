import fs from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";
import assert from "node:assert/strict";
import mysql from "mysql2/promise";
import { createClient } from "redis";
import { chromium } from "playwright";
const origin = "http://127.0.0.1:5173",
  base = "http://127.0.0.1:8080",
  nonce = crypto.randomBytes(3).toString("hex"),
  users = [],
  cookies = [],
  keys = [],
  checks = [];
const connect = (domain) =>
  mysql.createConnection({
    host: "127.0.0.1",
    port: Number(process.env.MENG_MYSQL_PORT),
    user: `meng_${domain}_app`,
    password: process.env[`MENG_MYSQL_${domain.toUpperCase()}_PASSWORD`],
    database: `meng_${domain}`,
  });
const redis = createClient({
  url: `redis://127.0.0.1:${process.env.MENG_REDIS_PORT}`,
  password: process.env.MENG_REDIS_PASSWORD,
});
let userDb, contentDb, growthDb, browser;
async function api(method, url, body, index = 0) {
  const response = await fetch(base + url, {
    method,
    headers: {
      Origin: origin,
      ...(cookies[index] ? { Cookie: cookies[index] } : {}),
      ...(body && !(body instanceof FormData)
        ? { "Content-Type": "application/json" }
        : {}),
    },
    body:
      body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
  });
  return { status: response.status, ...(await response.json()) };
}
function ok(result, status = 200) {
  assert.equal(result.status, status, JSON.stringify(result));
  return result.data;
}
async function check(name, run) {
  await run();
  checks.push(name);
  console.log("PASS " + name);
}
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
async function clearDraftRate(index = 0) {
  await redis.del("meng:content:limit:draft-interval:" + users[index]);
}
async function clearPostRate(index = 0) {
  await redis.del("meng:content:limit:post-interval:" + users[index]);
}
const basePost = {
  category: "DAILY",
  title: "Editor verification " + nonce,
  content: "# Markdown\n\nA **safe** post.",
  imageIds: [],
  tags: [],
};
try {
  for (let i = 0; i < 80; i++) {
    const health = await fetch("http://127.0.0.1:8102/actuator/health").catch(
      () => null,
    );
    if (health?.status === 200) break;
    await wait(500);
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
    const username = "verify_editor_" + nonce + "_" + i;
    const [insert] = await userDb.execute(
      "INSERT INTO user(username,password_hash,role,status,level) VALUES(?,?,'USER','NORMAL',1)",
      [username, admin.password_hash],
    );
    users.push(insert.insertId);
    const ticket = crypto.randomUUID(),
      key = "meng:captcha-ticket:" + ticket;
    keys.push(key);
    await redis.set(
      key,
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
  let image, cover, draft, post;
  const upload = async () => {
    const form = new FormData();
    form.append(
      "file",
      new Blob(
        [
          await fs.readFile(
            new URL(
              "../../vue_front/front_user/src/assets/forget_password_avatar.png",
              import.meta.url,
            ),
          ),
        ],
        { type: "image/png" },
      ),
      "image.png",
    );
    return ok(await api("POST", "/content/images", form));
  };
  await check("Draft and tag endpoints require login", async () => {
    for (const url of ["/content/drafts", "/content/tags"])
      ok(await api("GET", url, null, 3), 401);
  });
  await check(
    "Real image upload, separate cover and draft hydration",
    async () => {
      image = await upload();
      cover = await upload();
      draft = ok(
        await api("POST", "/content/drafts", {
          title: "Draft " + nonce,
          imageIds: [image.id],
          coverImageId: cover.id,
          tags: ["editor" + nonce],
        }),
      );
      const d = ok(await api("GET", "/content/drafts/" + draft));
      assert.deepEqual(
        d.images.map((i) => i.id),
        [image.id],
      );
      assert.equal(d.cover.id, cover.id);
      assert.equal(d.content, "");
    },
  );
  await check(
    "Draft throttling, ownership and empty draft validation",
    async () => {
      ok(await api("POST", "/content/drafts", { title: "rate" }), 429);
      for (const method of ["GET", "PUT", "DELETE"])
        ok(
          await api(
            method,
            "/content/drafts/" + draft,
            method === "PUT" ? { title: "foreign" } : null,
            1,
          ),
          403,
        );
      await clearDraftRate();
      ok(await api("POST", "/content/drafts", {}), 400);
    },
  );
  await check("Draft quota is 20, overwrites work at capacity", async () => {
    for (let i = 0; i < 19; i++) {
      await clearDraftRate();
      ok(await api("POST", "/content/drafts", { content: "draft " + i }));
    }
    await clearDraftRate();
    ok(await api("POST", "/content/drafts", { title: "twenty first" }), 409);
    await clearDraftRate();
    ok(
      await api("PUT", "/content/drafts/" + draft, {
        ...basePost,
        title: "Saved " + nonce,
        imageIds: [image.id],
        coverImageId: cover.id,
        tags: ["editor" + nonce],
      }),
    );
    assert.equal(ok(await api("GET", "/content/drafts")).length, 20);
  });
  await check(
    "Publish rollback retains draft, images and tag counts",
    async () => {
      await clearPostRate(1);
      ok(
        await api(
          "POST",
          "/content/posts",
          { ...basePost, imageIds: [image.id] },
          1,
        ),
        403,
      );
      await clearPostRate();
      ok(
        await api("POST", "/content/posts", {
          ...basePost,
          imageIds: [image.id],
          coverImageId: image.id,
          draftId: draft,
        }),
        400,
      );
      assert.equal(ok(await api("GET", "/content/drafts/" + draft)).id, draft);
    },
  );
  await check(
    "Publish consumes draft atomically and detail counts views once",
    async () => {
      await clearPostRate();
      post = ok(
        await api("POST", "/content/posts", {
          ...basePost,
          imageIds: [image.id],
          coverImageId: cover.id,
          tags: ["editor" + nonce, "ＡＢＣ" + nonce],
          draftId: draft,
        }),
      );
      ok(await api("GET", "/content/drafts/" + draft), 404);
      const d = ok(await api("GET", "/content/posts/" + post));
      assert.equal(d.cover.id, cover.id);
      assert.deepEqual(
        d.images.map((i) => i.id),
        [image.id],
      );
      assert.equal(d.viewCount, 1);
      assert.equal(ok(await api("GET", "/content/posts/" + post)).viewCount, 1);
      const feed = ok(await api("GET", "/content/posts?sort=LATEST"));
      assert.ok(
        feed.records.some((p) => p.id === post && p.images.length === 1),
      );
    },
  );
  await check(
    "Tag normalization, deduplication, prefix and usage count",
    async () => {
      await clearPostRate(1);
      ok(
        await api(
          "POST",
          "/content/posts",
          { ...basePost, tags: [" abc" + nonce + " ", "ＡＢＣ" + nonce] },
          1,
        ),
      );
      const [rows] = await contentDb.execute(
        "SELECT name,name_key,use_count FROM tag WHERE name_key=?",
        ["abc" + nonce],
      );
      assert.equal(rows.length, 1);
      assert.equal(rows[0].use_count, 2);
      assert.equal(
        ok(await api("GET", "/content/tags?keyword=abc" + nonce))[0],
        rows[0].name,
      );
      for (const tags of [
        ["has space"],
        ["#tag"],
        ["萌".repeat(13)],
        ["a", "b", "c", "d", "e", "f"],
      ]) {
        await clearPostRate();
        ok(await api("POST", "/content/posts", { ...basePost, tags }), 400);
      }
    },
  );
  await check(
    "Concurrent draft publication shares normalized tags without losing counts",
    async () => {
      const existing = ok(await api("GET", "/content/drafts"))[0].id;
      await clearDraftRate(1);
      const other = ok(
        await api("POST", "/content/drafts", { title: "parallel" }, 1),
      );
      await clearPostRate();
      await clearPostRate(1);
      const names = ["race" + nonce, "raceb" + nonce];
      const published = await Promise.all([
        api("POST", "/content/posts", {
          ...basePost,
          draftId: existing,
          tags: names,
        }),
        api(
          "POST",
          "/content/posts",
          { ...basePost, draftId: other, tags: [...names].reverse() },
          1,
        ),
      ]);
      published.forEach((result) => ok(result));
      const [tags] = await contentDb.execute(
        "SELECT name_key,use_count FROM tag WHERE name_key IN (?,?)",
        names,
      );
      assert.equal(tags.length, 2);
      assert.ok(tags.every((tag) => tag.use_count === 2));
    },
  );
  await check("Code-point text limits and image-count limit", async () => {
    await clearPostRate();
    ok(
      await api("POST", "/content/posts", {
        ...basePost,
        title: "😀".repeat(50),
      }),
    );
    ok(
      await api("POST", "/content/posts", {
        ...basePost,
        title: "😀".repeat(51),
      }),
      400,
    );
    ok(
      await api("POST", "/content/posts", {
        ...basePost,
        content: "😀".repeat(10001),
      }),
      400,
    );
    ok(
      await api("POST", "/content/posts", {
        ...basePost,
        imageIds: Array.from({ length: 11 }, (_, i) => i + 1),
      }),
      400,
    );
  });
  await check(
    "Editor browser: validation, formatting, preview safety, uploads, drafts and publication",
    async () => {
      browser = await chromium.launch({ channel: "msedge", headless: true });
      const context = await browser.newContext({
        viewport: { width: 1440, height: 1000 },
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
      const page = await context.newPage(),
        errors = [];
      page.on("pageerror", (error) => errors.push(error.message));
      await page.goto(origin);
      await page
        .getByRole("button", { name: "发一条新帖", exact: true })
        .click();
      await page
        .waitForURL("**/posts/new", { timeout: 8000 })
        .catch((error) => {
          console.log(JSON.stringify({ url: page.url(), errors }));
          throw error;
        });
      await page.locator(".cm-content").waitFor();
      assert.equal(await page.locator(".glow-cursor").count(), 0);
      assert.equal(await page.locator(".community-nav").count(), 0);
      await page.getByRole("button", { name: "发布", exact: true }).click();
      assert.equal(await page.locator(".field-error").count(), 2);
      await page.locator("#post-title").fill("Browser " + nonce);
      await page.locator(".cm-content").fill("选中文字");
      await page.locator(".cm-content").press("Control+a");
      await page.getByRole("button", { name: "加粗", exact: true }).click();
      assert.equal(
        await page.locator(".cm-content").innerText(),
        "**选中文字**",
      );
      await page
        .locator(".cm-content")
        .fill(
          "# Safe\n\n<script>window.__xss=1</script>\n[bad](javascript:alert(1))\n![image](https://example.com/a.png)\n[good](https://example.com)",
        );
      await page.getByRole("button", { name: "预览", exact: true }).click();
      assert.equal(
        await page.locator(".markdown-body script,.markdown-body img").count(),
        0,
      );
      const link = page.locator(".markdown-body a").first();
      assert.equal(await link.getAttribute("target"), "_blank");
      assert.match(await link.getAttribute("rel"), /noopener/);
      assert.equal(await page.evaluate(() => window.__xss), undefined);
      await page.getByRole("button", { name: "编辑", exact: true }).click();
      // Simulate one retryable failure while all successful uploads still hit the real service.
      let fail = true,
        active = 0,
        maxActive = 0;
      await page.route("**/api/content/images", async (route) => {
        active++;
        maxActive = Math.max(maxActive, active);
        await wait(150);
        if (fail) {
          fail = false;
          await route.fulfill({
            status: 400,
            contentType: "application/json",
            body: JSON.stringify({
              code: "COMMON_INVALID_ARGUMENT",
              message: "retry",
              data: null,
            }),
          });
        } else await route.continue();
        active--;
      });
      const bytes = await fs.readFile(
        new URL(
          "../../vue_front/front_user/src/assets/forget_password_avatar.png",
          import.meta.url,
        ),
      );
      await page
        .locator("input[type=file]")
        .first()
        .setInputFiles(
          Array.from({ length: 4 }, (_, i) => ({
            name: `fixture-${i}.png`,
            mimeType: "image/png",
            buffer: bytes,
          })),
        );
      await page
        .getByRole("button", { name: "重试上传", exact: true })
        .first()
        .waitFor();
      await page
        .getByRole("button", { name: "重试上传", exact: true })
        .first()
        .click();
      await page.waitForFunction(
        () =>
          document.querySelectorAll(".image-slot img").length === 4 &&
          !document.querySelector(".upload-progress"),
      );
      assert.ok(maxActive <= 3);
      await page.unroute("**/api/content/images");
      // Exercise resizing, drop, paste and cover fallback with real browser Files.
      await page.evaluate(async () => {
        const canvas = document.createElement("canvas");
        canvas.width = 3200;
        canvas.height = 1800;
        const data = await new Promise((resolve) =>
          canvas.toBlob(resolve, "image/png"),
        );
        const transfer = new DataTransfer();
        transfer.items.add(
          new File([data], "large.png", { type: "image/png" }),
        );
        document.querySelector(".media-panel").dispatchEvent(
          new DragEvent("drop", {
            bubbles: true,
            cancelable: true,
            dataTransfer: transfer,
          }),
        );
      });
      await page.waitForFunction(
        () =>
          document.querySelectorAll(".image-slot img").length === 5 &&
          !document.querySelector(".upload-progress"),
      );
      const uploaded = await page
        .locator(".image-slot img")
        .last()
        .getAttribute("src");
      const [[dimensions]] = await contentDb.execute(
        "SELECT width,height FROM post_image WHERE uploader_id=? ORDER BY id DESC LIMIT 1",
        [users[0]],
      );
      assert.deepEqual([dimensions.width, dimensions.height], [2560, 1440]);
      await page.evaluate(async (url) => {
        const data = await (await fetch(url)).blob();
        const transfer = new DataTransfer();
        transfer.items.add(
          new File([data], "paste.png", { type: "image/png" }),
        );
        document.querySelector(".post-editor-page").dispatchEvent(
          new ClipboardEvent("paste", {
            bubbles: true,
            cancelable: true,
            clipboardData: transfer,
          }),
        );
      }, uploaded);
      await page.waitForFunction(
        () =>
          document.querySelectorAll(".image-slot img").length === 6 &&
          !document.querySelector(".upload-progress"),
      );
      await page.locator("input[type=file]").last().setInputFiles({
        name: "cover.png",
        mimeType: "image/png",
        buffer: bytes,
      });
      await page
        .getByRole("button", { name: "移除封面", exact: true })
        .waitFor();
      await page.waitForFunction(
        () => !document.querySelector(".upload-progress"),
      );
      await page.getByRole("button", { name: "移除封面", exact: true }).click();
      assert.equal(await page.locator(".default-cover").count(), 1);
      const tag = page.getByRole("textbox", { name: "标签", exact: true });
      await tag.fill("editor" + nonce);
      await page
        .getByRole("button", { name: "editor" + nonce, exact: true })
        .waitFor();
      await tag.press("Enter");
      assert.equal(await page.locator(".editor-tag").count(), 1);
      await page.getByRole("link", { name: "回到主页", exact: true }).click();
      await page.getByRole("button", { name: "继续编辑", exact: true }).click();
      assert.ok(page.url().includes("/posts/new"));
      // Free a fixture draft slot so the browser can create its own draft.
      const all = ok(await api("GET", "/content/drafts"));
      ok(await api("DELETE", "/content/drafts/" + all[0].id));
      await clearDraftRate();
      await page
        .getByRole("button", { name: "保存为草稿", exact: true })
        .click();
      await page.waitForURL(/draft=\d+/);
      const browserDraft = Number(
        new URL(page.url()).searchParams.get("draft"),
      );
      await page.reload();
      await page.locator(".cm-content").waitFor();
      await page.waitForFunction(() =>
        document.querySelector("#post-title").value.startsWith("Browser"),
      );
      assert.equal(await page.locator(".image-slot img").count(), 6);
      assert.equal(await page.locator(".editor-tag").count(), 1);
      await page.screenshot({
        path: new URL(
          "../test-output/post-editor-desktop.png",
          import.meta.url,
        ).pathname.replace(/^\/(\w:)/, "$1"),
      });
      await page.setViewportSize({ width: 390, height: 844 });
      await wait(250);
      assert.ok(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth + 1,
        ),
      );
      await page.screenshot({
        path: new URL(
          "../test-output/post-editor-mobile.png",
          import.meta.url,
        ).pathname.replace(/^\/(\w:)/, "$1"),
      });
      await page.setViewportSize({ width: 1440, height: 1000 });
      await clearPostRate();
      await page.getByRole("button", { name: "发布", exact: true }).click();
      await page.waitForURL(/\/posts\/\d+$/);
      await page.locator(".post-detail article").waitFor();
      ok(await api("GET", "/content/drafts/" + browserDraft), 404);
      assert.equal(await page.locator(".detail-images button").count(), 6);
      assert.deepEqual(errors, []);
    },
  );
  // Wait for the fixture events to be consumed before removing their growth records.
  await check("Publication awards experience, drafts do not", async () => {
    for (let i = 0; i < 50; i++) {
      const [[row]] = await growthDb.execute(
        "SELECT COUNT(*) AS total FROM exp_record WHERE user_id IN (?,?) AND source='POST'",
        [...users],
      );
      if (row.total >= 5) break;
      await wait(100);
    }
    const [[row]] = await growthDb.execute(
      "SELECT COUNT(*) AS total FROM exp_record WHERE user_id IN (?,?)",
      [...users],
    );
    // Four posts by user 0 hit the documented daily cap of three; user 1 has two.
    assert.equal(row.total, 5);
  });
  console.log(JSON.stringify({ passed: true, checks: checks.length }));
} finally {
  await browser?.close();
  if (contentDb && users.length) {
    const [images] = await contentDb.query(
      "SELECT file_name,thumb_name FROM post_image WHERE uploader_id IN (?)",
      [users],
    );
    const [posts] = await contentDb.query(
      "SELECT id FROM post WHERE author_id IN (?)",
      [users],
    );
    const ids = posts.map((p) => p.id);
    if (ids.length) {
      await contentDb.query("DELETE FROM post_tag WHERE post_id IN (?)", [ids]);
      await contentDb.query("DELETE FROM post_like WHERE post_id IN (?)", [
        ids,
      ]);
      await contentDb.query("DELETE FROM post_favorite WHERE post_id IN (?)", [
        ids,
      ]);
    }
    await contentDb.query("DELETE FROM post_image WHERE uploader_id IN (?)", [
      users,
    ]);
    await contentDb.query("DELETE FROM post WHERE author_id IN (?)", [users]);
    await contentDb.query("DELETE FROM post_draft WHERE author_id IN (?)", [
      users,
    ]);
    await contentDb.query(
      "DELETE FROM post_draft_owner WHERE author_id IN (?)",
      [users],
    );
    await contentDb.execute("DELETE FROM tag WHERE name_key IN (?,?,?,?)", [
      "editor" + nonce,
      "abc" + nonce,
      "race" + nonce,
      "raceb" + nonce,
    ]);
    for (const image of images)
      for (const file of [image.file_name, image.thumb_name])
        await fs
          .unlink(path.join(process.env.MENG_CONTENT_IMAGE_ROOT, file))
          .catch(() => {});
  }
  await contentDb?.end();
  if (growthDb && users.length) {
    for (const table of ["exp_record", "check_in", "growth_account"])
      await growthDb.query("DELETE FROM " + table + " WHERE user_id IN (?)", [
        users,
      ]);
  }
  await growthDb?.end();
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
        MATCH: "meng:content:*",
        COUNT: 100,
      }))
        for (const key of batch)
          if (key.endsWith(":" + user)) await redis.del(key);
    }
    for (const key of keys) await redis.del(key);
    await redis.quit();
  }
  if (userDb && users.length)
    await userDb.query("DELETE FROM user WHERE id IN (?)", [users]);
  await userDb?.end();
}
