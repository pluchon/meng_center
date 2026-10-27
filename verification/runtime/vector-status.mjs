import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";
import mysql from "mysql2/promise";
import { createClient } from "redis";

export function assess({
  expectedIds,
  documents,
  encoding,
  building,
  pending,
  dimensions,
}) {
  const expected = new Set(expectedIds),
    actual = new Set(documents.map((row) => row.id));
  const missing = [...expected].filter((id) => !actual.has(id)).length;
  const extra = [...actual].filter((id) => !expected.has(id)).length;
  const obsolete = documents.filter(
    (row) =>
      typeof row.modelVersion !== "string" ||
      !row.modelVersion.endsWith(":1024:doc-v1") ||
      row.verdict === "BLOCK",
  ).length;
  return {
    checkedAt: new Date().toISOString(),
    status:
      building || pending
        ? "BUILDING"
        : !expected.size && !actual.size
          ? "EMPTY"
          : !missing &&
              !extra &&
              !obsolete &&
              actual.size === documents.length &&
              dimensions === 1024 &&
              encoding === "doc-v1"
            ? "CONSISTENT"
            : "NEEDS_REBUILD",
    publicPosts: expected.size,
    indexedPosts: documents.length,
    missingPosts: missing,
    extraPosts: extra,
    obsoleteOrBlockedVectors: obsolete,
    encoding,
    dimensions,
    building: Boolean(building),
    pending,
    versions: [
      ...new Set(documents.map((row) => row.modelVersion ?? "missing")),
    ].sort(),
    note: "只核对当前生效索引与重建进度，不调用模型、不切换别名、不推断历史上是否执行过重建命令。",
  };
}
async function main() {
  const ports = Object.fromEntries(
    (await readFile(new URL("../../deploy/ports.env", import.meta.url), "utf8"))
      .split(/\r?\n/)
      .filter((row) => /^MENG_\w+_PORT=\d+$/.test(row))
      .map((row) => row.split("=")),
  );
  if (
    !process.env.MENG_MYSQL_CONTENT_PASSWORD ||
    !process.env.MENG_ES_CONTENT_PASSWORD ||
    !process.env.MENG_REDIS_PASSWORD
  )
    throw new Error("缺少已有中间件凭据");
  const db = await mysql.createConnection({
    host: "127.0.0.1",
    port: Number(ports.MENG_MYSQL_PORT),
    user: "meng_content_app",
    password: process.env.MENG_MYSQL_CONTENT_PASSWORD,
    database: "meng_content",
    connectTimeout: 5000,
    supportBigNumbers: true,
    bigNumberStrings: true,
  });
  const redis = createClient({
    socket: {
      host: "127.0.0.1",
      port: Number(ports.MENG_REDIS_PORT),
      connectTimeout: 3000,
      reconnectStrategy: false,
    },
    password: process.env.MENG_REDIS_PASSWORD,
  });
  redis.on("error", () => {});
  const auth =
    "Basic " +
    Buffer.from(
      "meng_content_app:" + process.env.MENG_ES_CONTENT_PASSWORD,
    ).toString("base64");
  async function es(path, body) {
    const response = await fetch(
      `http://127.0.0.1:${ports.MENG_ES_PORT}${path}`,
      {
        method: body ? "POST" : "GET",
        headers: { Authorization: auth, "Content-Type": "application/json" },
        body: body ? JSON.stringify(body) : undefined,
        signal: AbortSignal.timeout(5000),
      },
    );
    if (response.status === 404) return null;
    if (!response.ok) throw new Error("索引查询不可用");
    return response.json();
  }
  try {
    await redis.connect();
    const [posts] = await db.query(
      "SELECT CAST(id AS CHAR) id FROM post WHERE delete_state=0 AND review_state='NORMAL'",
    );
    const [encoding, building, pending] = await Promise.all([
      redis.get("content:vector:encoding"),
      redis.get("content:vector:building"),
      redis.sCard("content:vector:pending"),
    ]);
    const mapping = await es("/meng_vector_post/_mapping");
    const dimensions = mapping
      ? Object.values(mapping)[0]?.mappings?.properties?.vector?.dims
      : null;
    const documents = [];
    if (mapping) {
      let after;
      for (let page = 0; page < 1000; page++) {
        const data = await es("/meng_vector_post/_search", {
          size: 500,
          _source: ["modelVersion", "verdict"],
          sort: [{ id: "asc" }],
          ...(after ? { search_after: after } : {}),
        });
        const hits = data.hits.hits;
        documents.push(
          ...hits.map((hit) => ({
            id: hit._id,
            modelVersion: hit._source.modelVersion,
            verdict: hit._source.verdict,
          })),
        );
        if (hits.length < 500) break;
        if (page === 999) throw new Error("索引过大，需使用完整快照核对");
        after = hits.at(-1).sort;
      }
    }
    process.stdout.write(
      JSON.stringify(
        assess({
          expectedIds: posts.map((row) => row.id),
          documents,
          encoding,
          building,
          pending,
          dimensions,
        }),
        null,
        2,
      ) + "\n",
    );
  } finally {
    await db.end();
    if (redis.isReady) await redis.quit();
    else if (redis.isOpen) redis.destroy();
  }
}
if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  main().catch(() => {
    process.stderr.write("向量只读核对失败，请检查中间件连接和现有权限。\n");
    process.exitCode = 1;
  });
}
