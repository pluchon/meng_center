import { readFile, mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { pathToFileURL } from "node:url";
import mysql from "mysql2/promise";

class DashboardError extends Error {}

const roles = new Set([
  "agent",
  "speech",
  "judge",
  "review",
  "writer",
  "assist",
  "memory",
  "vision",
  "embedding",
  "rerank",
  "image",
]);
const count = (value) => {
  if (typeof value !== "number" || !Number.isFinite(value) || value < 0)
    throw new DashboardError("指标必须是有限的非负数");
  return value;
};
export function daysEnding(end, length) {
  const day = new Date(end + "T00:00:00Z");
  if (
    !/^\d{4}-\d{2}-\d{2}$/.test(end) ||
    Number.isNaN(+day) ||
    day.toISOString().slice(0, 10) !== end ||
    !Number.isInteger(length) ||
    length < 1 ||
    length > 31
  )
    throw new DashboardError("日期必须有效，范围为 1～31 天");
  return Array.from({ length }, (_, i) =>
    new Date(+day - (length - i - 1) * 86400000).toISOString().slice(0, 10),
  );
}
export function aggregate(days, rounds, compactions) {
  const byDay = new Map(
    days.map((day) => [
      day,
      {
        day,
        rounds: 0,
        excludedRounds: 0,
        tokens: 0,
        rewrites: 0,
        unsupported: 0,
        tools: 0,
        unknownTools: 0,
        toolSuccesses: 0,
        idleCompactions: 0,
        capacityCompactions: 0,
        purposes: {},
      },
    ]),
  );
  for (const row of rounds) {
    const day = byDay.get(row.day);
    if (!day) throw new DashboardError("轮次日期不在报告范围内");
    const metrics =
      typeof row.metrics === "string" ? JSON.parse(row.metrics) : row.metrics;
    if (!metrics || !Array.isArray(metrics.calls)) {
      day.excludedRounds++;
      continue;
    }
    day.rounds++;
    day.rewrites += count(metrics.rewrites ?? 0) > 0 ? 1 : 0;
    day.unsupported += count(metrics.unsupported ?? 0) > 0 ? 1 : 0;
    for (const tool of metrics.tools ?? []) {
      day.tools++;
      if (typeof tool?.success !== "boolean") {
        day.unknownTools++;
        continue;
      }
      day.toolSuccesses += tool.success ? 1 : 0;
    }
    for (const call of metrics.calls) {
      const purpose = roles.has(call.purpose) ? call.purpose : "other";
      const current = (day.purposes[purpose] ??= {
        calls: 0,
        input: 0,
        output: 0,
        cached: 0,
      });
      const input = count(call.input ?? 0),
        output = count(call.output ?? 0),
        cached = count(call.cached ?? 0);
      if (cached > input)
        throw new DashboardError("缓存 token 不能超过输入 token");
      current.calls++;
      current.input += input;
      current.output += output;
      current.cached += cached;
      day.tokens += input + output;
    }
  }
  for (const row of compactions) {
    const day = byDay.get(row.day);
    if (!day) throw new DashboardError("压缩日期不在报告范围内");
    day.idleCompactions = count(Number(row.idle_compactions));
    day.capacityCompactions = count(Number(row.capacity_compactions));
  }
  return {
    generatedAt: new Date().toISOString(),
    timezone: "UTC",
    days: [...byDay.values()],
  };
}
const escape = (text) =>
  String(text).replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const ratio = (a, b) => (b ? ((100 * a) / b).toFixed(1) + "%" : "—");
const integer = (value) => Math.round(value).toLocaleString("zh-CN");
function group(day, speech) {
  return Object.entries(day.purposes)
    .filter(([role]) =>
      speech
        ? role === "speech"
        : [
            "agent",
            "judge",
            "review",
            "writer",
            "assist",
            "memory",
            "vision",
          ].includes(role),
    )
    .reduce(
      (sum, [, item]) => ({
        input: sum.input + item.input,
        cached: sum.cached + item.cached,
      }),
      { input: 0, cached: 0 },
    );
}
export function render(report) {
  const rows = report.days
    .map((day) => {
      const speech = group(day, true),
        decisions = group(day, false);
      return `<tr><th scope="row">${escape(day.day)}</th><td>${integer(day.rounds)}</td><td>${integer(day.excludedRounds)}</td><td>${day.rounds ? integer(day.tokens / day.rounds) : "—"}</td><td>${ratio(speech.cached, speech.input)}</td><td>${ratio(decisions.cached, decisions.input)}</td><td>${integer(day.idleCompactions)} / ${integer(day.capacityCompactions)}</td><td>${ratio(day.toolSuccesses, day.tools - day.unknownTools)}${day.unknownTools ? `（${integer(day.unknownTools)} 次状态未知）` : ""}</td><td>${ratio(day.rewrites, day.rounds)}</td><td>${ratio(day.unsupported, day.rounds)}</td></tr>`;
    })
    .join("");
  const purposes = report.days
    .flatMap((day) =>
      Object.entries(day.purposes).map(
        ([role, item]) =>
          `<tr><th scope="row">${escape(day.day)}</th><td>${escape(role)}</td><td>${integer(item.calls)}</td><td>${integer(item.input)}</td><td>${integer(item.output)}</td><td>${integer(item.cached)}</td><td>${ratio(item.cached, item.input)}</td></tr>`,
      ),
    )
    .join("");
  return `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>奶糖运行时每日看板</title><style>
  :root{color-scheme:light;font:15px/1.6 system-ui,sans-serif;color:#223f4a;background:#f1f7fa}body{max-width:1320px;margin:40px auto;padding:0 24px}h1{font-size:28px;margin-bottom:4px}h2{font-size:20px}p{color:#52707b}.table{overflow:auto;border:1px solid #d6e5ea;border-radius:12px;background:white;margin:20px 0}table{border-collapse:collapse;width:100%;white-space:nowrap}th,td{text-align:right;padding:13px 16px;border-bottom:1px solid #e8eff2}th:first-child{text-align:left}thead{background:#e7f2f7}caption{text-align:left;padding:16px;font-weight:700}.note{border-left:4px solid #79b4cc;padding:12px 20px;background:#fff}footer{color:#647e88;font-size:13px}@media(max-width:600px){body{margin:20px auto;padding:0 12px}h1{font-size:23px}}
  </style><h1>奶糖运行时每日看板</h1><p>UTC 自然日 · ${escape(report.days[0].day)} 至 ${escape(report.days.at(-1).day)} · 静态只读快照</p><div class="note">只统计已落库的完成轮次；同轮多个气泡只计一次。后台独立任务与未完成请求不计入轮次。旧格式缺少调用指标的轮次单独列出，不按零 token 纳入平均数。没有分母时显示“—”，不能据此判断缓存或质量达标。压缩次数从新增统计表启用后累计。</div><div class="table"><table><caption>每日运行情况</caption><thead><tr><th scope="col">日期</th><th scope="col">轮次</th><th scope="col">旧格式未纳入</th><th scope="col">平均 token / 轮</th><th scope="col">说话缓存命中</th><th scope="col">判断侧缓存命中</th><th scope="col">空闲 / 容量压缩</th><th scope="col">工具成功率</th><th scope="col">重写轮次率</th><th scope="col">末次检查有无依据说法的轮次率</th></tr></thead><tbody>${rows}</tbody></table></div><div class="table"><table><caption>按用途核对 token（缓存命中率 = 缓存 token / 输入 token）</caption><thead><tr><th>日期</th><th>用途</th><th>调用</th><th>输入</th><th>输出</th><th>缓存</th><th>命中率</th></tr></thead><tbody>${purposes || '<tr><td colspan="7">所选日期没有可统计的调用样本</td></tr>'}</tbody></table></div><footer>生成于 ${escape(report.generatedAt)}。不读取消息正文，不导出用户编号、会话编号或模型理由。无依据说法指标取末次检查记录，包含触发固定兜底的候选，不等同已发布错误率。此文件不自动刷新。</footer></html>`;
}
async function main() {
  const [
    end = new Date().toISOString().slice(0, 10),
    length = "7",
    output = "../test-output/ai-runtime-dashboard.html",
  ] = process.argv.slice(2);
  const days = daysEnding(end, Number(length)),
    after = new Date(new Date(end + "T00:00:00Z").getTime() + 86400000)
      .toISOString()
      .slice(0, 10);
  const portText = await readFile(
    new URL("../../deploy/ports.env", import.meta.url),
    "utf8",
  );
  const port = Number(portText.match(/^MENG_MYSQL_PORT=(\d+)$/m)?.[1]);
  const password = process.env.MENG_MYSQL_COMPANION_PASSWORD;
  if (!password || !port)
    throw new DashboardError("缺少已配置的 companion 数据库凭据或端口");
  const connection = await mysql.createConnection({
    host: "127.0.0.1",
    port,
    user: "meng_companion_app",
    password,
    database: "meng_companion",
    connectTimeout: 5000,
    timezone: "Z",
    dateStrings: true,
  });
  try {
    // 不选择正文；首条角色气泡代表一轮，避免多气泡把调用与 token 重复相加。
    await connection.query("SET TRANSACTION READ ONLY");
    await connection.beginTransaction();
    const [rounds] = await connection.execute(
      `SELECT DATE_FORMAT(m.create_time,'%Y-%m-%d') day,m.metrics FROM companion_message m JOIN companion_session s ON s.id=m.session_id AND s.character_code='NEKO' WHERE m.role='CHARACTER' AND m.turn_id IS NOT NULL AND m.metrics IS NOT NULL AND m.create_time>=? AND m.create_time<? AND NOT EXISTS(SELECT 1 FROM companion_message earlier WHERE earlier.turn_id=m.turn_id AND earlier.role='CHARACTER' AND earlier.metrics IS NOT NULL AND earlier.id<m.id) ORDER BY m.id`,
      [days[0], after],
    );
    const [compactions] = await connection.execute(
      `SELECT DATE_FORMAT(day_utc,'%Y-%m-%d') day,idle_compactions,capacity_compactions FROM companion_runtime_daily WHERE day_utc>=? AND day_utc<? ORDER BY day_utc`,
      [days[0], after],
    );
    const report = aggregate(days, rounds, compactions);
    await connection.commit();
    const path = resolve(output);
    if (!path.endsWith(".html"))
      throw new DashboardError("输出文件必须为 .html");
    await mkdir(dirname(path), { recursive: true });
    await writeFile(path, render(report), { flag: "wx", encoding: "utf8" });
    process.stdout.write(
      `Dashboard: ${path}\nDays: ${days.length}; completed rounds: ${report.days.reduce((sum, day) => sum + day.rounds, 0)}\n`,
    );
  } finally {
    await connection.end();
  }
}
if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  main().catch((error) => {
    process.stderr.write(
      `看板导出失败（${error instanceof DashboardError ? error.message : error.code || error.name}），请检查日期、数据库连接、迁移和输出文件是否已存在。\n`,
    );
    process.exitCode = 1;
  });
}
