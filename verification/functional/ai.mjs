import assert from "node:assert/strict";
import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import mysql from "mysql2/promise";
import amqp from "amqplib";
import { createClient } from "redis";
import { cleanupImFixtures } from "./im-fixture-cleanup.mjs";
import { cleanupPostReadModels } from "./read-model-cleanup.mjs";

// 仅操作随机测试账号及其派生数据；真实模型调用会消耗少量 DashScope 额度。
const base = "http://127.0.0.1:8080";
const origin = "http://127.0.0.1:5173";
const nonce = crypto.randomBytes(5).toString("hex");
const users = [],
  cookies = [],
  posts = [],
  events = [],
  checks = [];
const newTags = [];
const redis = createClient({
  url: `redis://127.0.0.1:${process.env.MENG_REDIS_PORT}`,
  password: process.env.MENG_REDIS_PASSWORD,
});
redis.on("error", () => {});
let userDb, contentDb, growthDb, broker, channel;
const connect = (domain) =>
  mysql.createConnection({
    host: "127.0.0.1",
    port: Number(process.env.MENG_MYSQL_PORT),
    user: `meng_${domain}_app`,
    password: process.env[`MENG_MYSQL_${domain.toUpperCase()}_PASSWORD`],
    database: `meng_${domain}`,
    dateStrings: true,
  });
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
async function eventually(read, description, timeout = 45_000) {
  const deadline = Date.now() + timeout;
  do {
    if (await read()) return;
    await wait(250);
  } while (Date.now() < deadline);
  throw Error(`Timed out: ${description}`);
}
async function api(method, url, body, account = 0) {
  const response = await fetch(base + url, {
    method,
    headers: {
      Origin: origin,
      ...(cookies[account] ? { Cookie: cookies[account] } : {}),
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(70_000),
  });
  return { status: response.status, ...(await response.json()) };
}
function ok(response, status = 200) {
  assert.equal(
    response.status,
    status,
    JSON.stringify({
      status: response.status,
      code: response.code,
      message: response.message,
    }),
  );
  return response.data;
}
async function check(name, run) {
  await run();
  checks.push(name);
  process.stdout.write(`PASS ${name}\n`);
}
function resultId(input, type) {
  const namespace = Buffer.from("6ba7b8119dad11d180b400c04fd430c8", "hex");
  const value = crypto
    .createHash("sha1")
    .update(namespace)
    .update(input + ":" + type)
    .digest()
    .subarray(0, 16);
  value[6] = (value[6] & 15) | 80;
  value[8] = (value[8] & 63) | 128;
  const hex = value.toString("hex");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
async function publish(type, body, id = crypto.randomUUID()) {
  events.push(id);
  channel.publish(
    "meng.ai.topic",
    type,
    Buffer.from(
      JSON.stringify({
        eventId: id,
        eventType: type,
        traceId: id,
        occurredAt: new Date().toISOString(),
        body,
      }),
    ),
    { contentType: "application/json", deliveryMode: 2 },
  );
  await channel.waitForConfirms();
  return id;
}
async function cleanup() {
  if (contentDb && users.length) {
    const [outbox] = await contentDb.query(
      "SELECT event_id,event_type FROM ai_event_outbox WHERE JSON_EXTRACT(body,'$.body.authorId') IN (?) OR JSON_EXTRACT(body,'$.body.userId') IN (?)",
      [users, users],
    );
    events.push(
      ...outbox.map((row) =>
        resultId(
          row.event_id,
          row.event_type === "comment.created"
            ? "comment.moderated"
            : "post.analyzed",
        ),
      ),
    );
    await contentDb.query("DELETE FROM ai_event_outbox WHERE event_id IN (?)", [
      outbox.map((row) => row.event_id).concat("none"),
    ]);
    const [imageRows] = await contentDb.query(
      "SELECT file_name,thumb_name FROM post_image WHERE uploader_id IN (?)",
      [users],
    );
    for (const row of imageRows)
      for (const name of [row.file_name, row.thumb_name]) {
        const root = path.resolve(process.env.MENG_CONTENT_IMAGE_ROOT),
          file = path.resolve(root, name);
        assert.ok(file.startsWith(root + path.sep));
        await fs.rm(file, { force: true });
      }
    const [commentRows] = await contentDb.query(
      "SELECT id FROM comment WHERE author_id IN (?)",
      [users],
    );
    const comments = commentRows.map((row) => row.id);
    if (comments.length) {
      for (const table of [
        "comment_ai_review",
        "comment_like",
        "comment_mention",
        "comment_image",
      ])
        await contentDb.query(`DELETE FROM ${table} WHERE comment_id IN (?)`, [
          comments,
        ]);
      await contentDb.query("DELETE FROM comment WHERE id IN (?)", [comments]);
    }
    if (posts.length) {
      await contentDb.query(
        "UPDATE tag t JOIN (SELECT tag_id,COUNT(*) n FROM post_tag WHERE post_id IN (?) GROUP BY tag_id) d ON d.tag_id=t.id SET t.use_count=GREATEST(0,t.use_count-d.n)",
        [posts],
      );
      for (const table of [
        "post_ai_feature",
        "post_tag",
        "post_like",
        "post_favorite",
        "recommend_feedback",
        "recommend_log",
      ])
        await contentDb.query(`DELETE FROM ${table} WHERE post_id IN (?)`, [
          posts,
        ]);
      await contentDb.query("DELETE FROM post WHERE id IN (?)", [posts]);
      await cleanupPostReadModels(redis, posts);
      if (newTags.length)
        await contentDb.query(
          "DELETE FROM tag WHERE id IN (?) AND use_count=0",
          [newTags],
        );
      const auth =
        "Basic " +
        Buffer.from(
          `meng_content_app:${process.env.MENG_ES_CONTENT_PASSWORD}`,
        ).toString("base64");
      for (const id of posts)
        for (const color of ["blue", "green"]) {
          const response = await fetch(
            `http://127.0.0.1:${process.env.MENG_ES_PORT}/meng_vector_post_${color}/_doc/${id}`,
            { method: "DELETE", headers: { Authorization: auth } },
          );
          assert.ok(response.ok || response.status === 404);
        }
    }
    await contentDb.query("DELETE FROM post_image WHERE uploader_id IN (?)", [
      users,
    ]);
    await contentDb.query(
      "DELETE FROM author_daily_stat WHERE author_id IN (?)",
      [users],
    );
    await contentDb.query("DELETE FROM favorite_folder WHERE user_id IN (?)", [
      users,
    ]);
    await contentDb.query("DELETE FROM recommend_log WHERE user_id IN (?)", [
      users,
    ]);
    if (events.length)
      await contentDb.query(
        "DELETE FROM ai_result_inbox WHERE event_id IN (?)",
        [events],
      );
  }
  if (growthDb && users.length)
    for (const table of [
      "point_record",
      "point_account",
      "exp_record",
      "check_in",
      "growth_account",
    ])
      await growthDb.query(`DELETE FROM ${table} WHERE user_id IN (?)`, [
        users,
      ]);
  if (userDb && users.length) {
    await cleanupImFixtures(users);
    await userDb.query(
      "DELETE FROM profile_ai_outbox WHERE JSON_EXTRACT(body,'$.body.userId') IN (?)",
      [users],
    );
    await userDb.query("DELETE FROM profile_ai_review WHERE user_id IN (?)", [
      users,
    ]);
    await userDb.query(
      "DELETE FROM user_follow WHERE follower_id IN (?) OR followee_id IN (?)",
      [users, users],
    );
    await userDb.query("DELETE FROM user WHERE id IN (?)", [users]);
  }
  if (redis.isOpen) {
    for (const cookie of cookies.filter(Boolean)) {
      const claims = JSON.parse(
        Buffer.from(cookie.split("=")[1].split(".")[1], "base64url"),
      );
      await redis.del("meng:session:" + claims.sessionId);
    }
    for await (const batch of redis.scanIterator({ MATCH: "*", COUNT: 200 }))
      for (const key of batch) {
        const owned = users.some((id) =>
          new RegExp(
            `^(meng:user-sessions:${id}$|meng:content:limit:[^:]+:${id}(?:$|:)|meng:content:viewed:[^:]+:${id}$|content:rec:(?:profile|vec|vec-version|seen|feed|mute-author):${id}(?:$|:)|content:ai:quota:[^:]+:${id}:|content:ai:creator-review:${id}:|growth:ai:summary:${id}:|growth:ai:quota:summary:${id}:)`,
          ).test(key),
        );
        if (owned) await redis.del(key);
        if (posts.some((id) => key === "content:related:" + id))
          await redis.del(key);
      }
    await redis.quit();
  }
  await channel?.close();
  await broker?.close();
  await contentDb?.end();
  await growthDb?.end();
  await userDb?.end();
}
try {
  await redis.connect();
  userDb = await connect("user");
  contentDb = await connect("content");
  growthDb = await connect("growth");
  broker = await amqp.connect({
    hostname: "127.0.0.1",
    port: Number(process.env.MENG_RABBITMQ_PORT),
    username: process.env.MENG_RABBITMQ_USERNAME,
    password: process.env.MENG_RABBITMQ_PASSWORD,
    vhost: "meng",
  });
  channel = await broker.createConfirmChannel();
  const [[admin]] = await userDb.execute(
    "SELECT password_hash FROM user WHERE username=?",
    [process.env.MENG_ADMIN_USERNAME],
  );
  assert.ok(admin);
  for (let i = 0; i < 6; i++) {
    const username = `verify_ai_${nonce}_${i}`;
    const [insert] = await userDb.execute(
      "INSERT INTO user(username,password_hash,role,status,level) VALUES(?,?,'USER','NORMAL',1)",
      [username, admin.password_hash],
    );
    users.push(insert.insertId);
    if (i > 1) continue;
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
    cookies[i] = response.headers.get("set-cookie").split(";")[0];
  }
  await check(
    "Authentication and empty-draft validation precede model calls",
    async () => {
      ok(
        await api("POST", "/content/ai/tag-suggestions", {
          category: "TECH",
          title: "",
          content: "",
        }),
        400,
      );
      ok(await api("POST", "/growth/ai-summaries", { days: 8 }), 400);
      ok(await api("GET", "/content/admin/ai/reviews"), 403);
      const response = await fetch(base + "/growth/ai-summaries", {
        method: "POST",
        headers: { Origin: origin, "Content-Type": "application/json" },
        body: '{"days":7}',
      });
      assert.equal(response.status, 401);
    },
  );
  const draft = {
    category: "TECH",
    title: "Vue 组件渲染提速实践",
    content:
      "用 computed 缓存派生数据，按路由拆分图表和弹窗，列表使用稳定 key，避免重复计算。",
    selectedTags: [],
  };
  await check(
    "Real tag and inspiration chains return structured suggestions",
    async () => {
      const tags = ok(await api("POST", "/content/ai/tag-suggestions", draft));
      assert.ok(tags.tags.length > 0 && tags.tags.length <= 3);
      assert.ok(tags.tags.every((tag) => typeof tag.name === "string"));
      const ideas = ok(await api("POST", "/content/ai/inspirations", draft));
      assert.equal(ideas.inspirations.length, 3);
      assert.ok(ideas.inspirations.every((item) => item.opening && item.angle));
    },
  );
  let realPost;
  await check(
    "Publication reaches Python, stores features and a 1024-dimensional ES vector",
    async () => {
      const [existingTags] = await contentDb.query(
        "SELECT id FROM tag WHERE name='Vue'",
      );
      realPost = ok(
        await api(
          "POST",
          "/content/posts",
          { ...draft, imageIds: [], tags: ["Vue"] },
          1,
        ),
      );
      posts.push(realPost);
      const [attachedTags] = await contentDb.execute(
        "SELECT tag_id FROM post_tag WHERE post_id=?",
        [realPost],
      );
      for (const row of attachedTags)
        if (!existingTags.some((tag) => tag.id === row.tag_id))
          newTags.push(row.tag_id);
      await eventually(async () => {
        const [rows] = await contentDb.execute(
          "SELECT * FROM post_ai_feature WHERE post_id=?",
          [realPost],
        );
        return rows.length > 0;
      }, "post analysis");
      const [rows] = await contentDb.execute(
        "SELECT verdict,analyze_state,model_version FROM post_ai_feature WHERE post_id=?",
        [realPost],
      );
      assert.equal(rows[0].analyze_state, "SUCCESS");
      assert.equal(rows[0].verdict, "PASS");
      const response = await fetch(
        `http://127.0.0.1:${process.env.MENG_ES_PORT}/meng_vector_post/_doc/${realPost}?_source_exclude_vectors=false`,
        {
          headers: {
            Authorization:
              "Basic " +
              Buffer.from(
                `meng_content_app:${process.env.MENG_ES_CONTENT_PASSWORD}`,
              ).toString("base64"),
          },
        },
      );
      const doc = await response.json();
      assert.equal(doc._source.vector.length, 1024);
      ok(await api("PUT", `/content/posts/${realPost}/like`));
      await eventually(async () => {
        const vector = await redis.get(`content:rec:vec:${users[0]}`);
        return vector && Buffer.from(vector, "base64").length === 4096;
      }, "interest vector from mget");
    },
  );
  await check(
    "Semantic search and related posts use the computed vector",
    async () => {
      await wait(1200);
      const result = ok(
        await api(
          "GET",
          "/content/search/posts?sort=RELEVANT&q=" +
            encodeURIComponent("前端性能优化"),
        ),
      );
      assert.equal(result.semantic, true);
      assert.ok(result.records.some((post) => post.id === realPost));
      assert.ok(
        Array.isArray(
          ok(await api("GET", `/content/posts/${realPost}/related`)),
        ),
      );
    },
  );
  await check(
    "Growth and creator summaries retain SQL numbers and reuse unchanged cache",
    async () => {
      ok(await api("POST", "/growth/check-ins"));
      const first = ok(await api("POST", "/growth/ai-summaries", { days: 7 }));
      assert.equal(first.stats.checkInDays, 1);
      assert.equal(
        first.stats.dailyExperience.reduce((sum, day) => sum + day.amount, 0),
        5,
      );
      assert.equal(first.result.suggestions.length, 1);
      const before = await redis.get(
        `growth:ai:quota:summary:${users[0]}:${new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Taipei" }).format(new Date())}`,
      );
      assert.deepEqual(
        ok(await api("POST", "/growth/ai-summaries", { days: 7 })),
        first,
      );
      const second = ok(
        await api("POST", "/content/creator/ai-reviews", { days: 7 }, 1),
      );
      assert.equal(second.stats.postCount, 1);
      assert.equal(second.stats.dailyMetrics.length, 7);
      assert.ok(second.result.summary);
      assert.deepEqual(
        ok(await api("POST", "/content/creator/ai-reviews", { days: 7 }, 1)),
        second,
      );
      assert.ok(before);
    },
  );
  await check(
    "Comment moderation and edited post versions reach the owning domain",
    async () => {
      const comment = ok(
        await api("POST", `/content/posts/${realPost}/comments`, {
          content: "这个优化思路很清楚，谢谢分享。",
          imageIds: [],
        }),
      );
      await eventually(async () => {
        const [rows] = await contentDb.execute(
          "SELECT verdict FROM comment_ai_review WHERE comment_id=?",
          [comment.id],
        );
        return rows[0]?.verdict === "PASS";
      }, "comment moderation");
      ok(
        await api(
          "PUT",
          `/content/posts/${realPost}`,
          {
            ...draft,
            title: draft.title + "（更新）",
            imageIds: [],
            tags: ["Vue"],
          },
          1,
        ),
      );
      await eventually(async () => {
        const [[row]] = await contentDb.execute(
          "SELECT COUNT(*) n FROM post_ai_feature f JOIN post p ON p.id=f.post_id AND p.update_time=f.post_version WHERE p.id=? AND f.analyze_state='SUCCESS'",
          [realPost],
        );
        return row.n === 1;
      }, "edited post analysis");
    },
  );
  await check(
    "Cold recommendation pagination, author spacing and negative feedback",
    async () => {
      for (let i = 1; i < 6; i++)
        for (let j = 0; j < 2; j++) {
          const [insert] = await contentDb.execute(
            "INSERT INTO post(author_id,category,title,content,summary,hot_score) VALUES(?,?,?,?,?,?)",
            [
              users[i],
              j ? "DAILY" : "TECH",
              `AI 回归 ${nonce} ${i} ${j}`,
              "随机回归数据",
              "随机回归数据",
              100 - i,
            ],
          );
          posts.push(insert.insertId);
        }
      const first = ok(
        await api("GET", "/content/posts?sort=RECOMMEND&size=2&page=1"),
      );
      const second = ok(
        await api("GET", "/content/posts?sort=RECOMMEND&size=2&page=2"),
      );
      assert.ok(first.records.length && second.records.length);
      assert.ok(
        first.records.every((a) => !second.records.some((b) => a.id === b.id)),
      );
      const list = ok(
        await api("GET", "/content/posts?sort=RECOMMEND&size=20&page=1"),
      ).records;
      // 作者间隔只是尽量保证：后面再没有别的作者可选时允许重复
      for (let i = 0; i < list.length; i++) {
        const previous = list
          .slice(Math.max(0, i - 4), i)
          .map((post) => post.author.id);
        if (previous.includes(list[i].author.id))
          assert.ok(
            list.slice(i).every((post) => previous.includes(post.author.id)),
            "Author repeated while another author was available",
          );
      }
      const fixture = list.find((post) => posts.includes(post.id));
      assert.ok(fixture, "Fixture must appear before testing feedback");
      const removed = fixture.id;
      ok(
        await api("POST", "/content/recommendations/feedback", {
          postId: removed,
        }),
      );
      ok(
        await api("POST", "/content/recommendations/feedback", {
          postId: removed,
        }),
      );
      const [[count]] = await contentDb.execute(
        "SELECT COUNT(*) n FROM recommend_feedback WHERE user_id=? AND post_id=?",
        [users[0], removed],
      );
      assert.equal(count.n, 1);
      assert.ok(
        !ok(
          await api("GET", "/content/posts?sort=RECOMMEND&size=20&page=2"),
        ).records.some((post) => post.id === removed),
      );
    },
  );
  await check(
    "Profile events are audited without clearing public fields while enforcement is disabled",
    async () => {
      ok(
        await api("PUT", "/user/me/profile", {
          nickname: "云朵回归",
          bio: "喜欢分享技术和日常",
          username: `verify_ai_${nonce}_0`,
        }),
      );
      await eventually(async () => {
        const [rows] = await userDb.execute(
          "SELECT id FROM profile_ai_review WHERE user_id=?",
          [users[0]],
        );
        return rows.length > 0;
      }, "profile moderation");
      const [[row]] = await userDb.execute(
        "SELECT nickname FROM user WHERE id=?",
        [users[0]],
      );
      assert.equal(row.nickname, "云朵回归");
    },
  );
  await check(
    "Old analysis is ignored and duplicate BLOCK result stays public with enforcement disabled",
    async () => {
      const [[post]] = await contentDb.execute(
        "SELECT update_time FROM post WHERE id=?",
        [realPost],
      );
      const body = {
        postId: realPost,
        version: post.update_time.replace(" ", "T"),
        modelVersion: "verification",
        topics: ["Vue"],
        keywords: [],
        quality: 3,
        verdict: "BLOCK",
        labels: ["回归边界"],
        reason: "仅验证结果消费边界",
        embedding: Array(1024).fill(0.01),
        analyzeState: "SUCCESS",
        imagesChanged: true,
        imageReviews: [],
      };
      await publish("post.analyzed", {
        ...body,
        version: "2000-01-01T00:00:00",
      });
      await wait(500);
      const [[prior]] = await contentDb.execute(
        "SELECT model_version FROM post_ai_feature WHERE post_id=?",
        [realPost],
      );
      assert.notEqual(prior.model_version, "verification");
      const id = await publish("post.analyzed", body);
      await publish("post.analyzed", body, id);
      await eventually(async () => {
        const [[row]] = await contentDb.execute(
          "SELECT model_version FROM post_ai_feature WHERE post_id=?",
          [realPost],
        );
        return row.model_version === "verification";
      }, "result consumption");
      const [[row]] = await contentDb.execute(
        "SELECT review_state FROM post WHERE id=?",
        [realPost],
      );
      assert.equal(row.review_state, "NORMAL");
      const [[count]] = await contentDb.execute(
        "SELECT COUNT(*) n FROM ai_result_inbox WHERE event_id=?",
        [id],
      );
      assert.equal(count.n, 1);
    },
  );
  if (process.argv.includes("--covers"))
    await check(
      "Generated cover passes Java upload processing and is marked AI-generated",
      async () => {
        const image = ok(await api("POST", "/content/ai/covers", draft));
        assert.ok(image.id && image.width > 0 && image.thumbUrl);
        const [[row]] = await contentDb.execute(
          "SELECT ai_generated FROM post_image WHERE id=?",
          [image.id],
        );
        assert.equal(row.ai_generated, 1);
      },
    );
  process.stdout.write(
    JSON.stringify({ passed: true, checks: checks.length }) + "\n",
  );
} finally {
  await cleanup();
}
