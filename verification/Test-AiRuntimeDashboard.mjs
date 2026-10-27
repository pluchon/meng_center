import assert from "node:assert/strict";
import { test } from "node:test";
import { aggregate, daysEnding, render } from "./runtime/dashboard.mjs";

test("按 UTC 跨月生成日期，拒绝无效日期与过大范围", () => {
  assert.deepEqual(daysEnding("2026-03-01", 2), ["2026-02-28", "2026-03-01"]);
  assert.throws(() => daysEnding("2026-02-30", 2));
  assert.throws(() => daysEnding("2026-10-27", 32));
});
test("按轮次统计 token、工具、重写与无依据说法，不导出原始指标", () => {
  const metrics = {
    rewrites: 1,
    unsupported: 2,
    rewriteReason: "private-fixture",
    calls: [
      { purpose: "speech", input: 1000, output: 20, cached: 700 },
      { purpose: "judge", input: 100, output: 30, cached: 50 },
    ],
    tools: [{ success: true }, { success: false }],
  };
  const report = aggregate(
    ["2026-10-27"],
    [{ day: "2026-10-27", metrics }],
    [{ day: "2026-10-27", idle_compactions: "2", capacity_compactions: "1" }],
  );
  assert.equal(report.days[0].tokens, 1150);
  assert.equal(report.days[0].unsupported, 1);
  assert.equal(report.days[0].toolSuccesses, 1);
  const html = render(report);
  assert.ok(html.includes("70.0%") && html.includes("50.0%"));
  assert.ok(
    !JSON.stringify(report).includes("private-fixture") &&
      !html.includes("private-fixture"),
  );
});
test("没有样本时不伪造成功率", () => {
  const report = aggregate(["2026-10-27"], [], []);
  const html = render(report);
  assert.ok(html.includes("没有可统计的调用样本") && html.includes("—"));
  assert.ok(!html.includes("100.0%"));
});
test("不兼容的历史轮次单独列出，不按零 token 纳入平均数", () => {
  const report = aggregate(
    ["2026-10-27"],
    [{ day: "2026-10-27", metrics: { oldField: true } }],
    [],
  );
  assert.equal(report.days[0].rounds, 0);
  assert.equal(report.days[0].excludedRounds, 1);
});
test("历史工具缺少状态时单独标注，不猜测成功或失败", () => {
  const report = aggregate(
    ["2026-10-27"],
    [
      {
        day: "2026-10-27",
        metrics: { calls: [], tools: ["search_posts", { success: true }] },
      },
    ],
    [],
  );
  assert.equal(report.days[0].unknownTools, 1);
  assert.equal(report.days[0].toolSuccesses, 1);
  assert.ok(render(report).includes("1 次状态未知"));
});
test("非法 token 和缓存计数拒绝生成报告", () => {
  for (const call of [
    { input: -1 },
    { input: 1, cached: 2 },
    { input: Infinity },
  ])
    assert.throws(() =>
      aggregate(
        ["2026-10-27"],
        [{ day: "2026-10-27", metrics: { calls: [call] } }],
        [],
      ),
    );
});
