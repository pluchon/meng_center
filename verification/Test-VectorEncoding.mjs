import assert from "node:assert/strict";
import { test } from "node:test";
import { assess } from "./runtime/vector-status.mjs";
const valid = {
  expectedIds: ["1"],
  documents: [
    { id: "1", modelVersion: "embedding-model:1024:doc-v1", verdict: "PASS" },
  ],
  encoding: "doc-v1",
  building: null,
  pending: 0,
  dimensions: 1024,
};
test("当前公开帖子与 document 向量完全一致才确认就绪", () => {
  assert.equal(assess(valid).status, "CONSISTENT");
  assert.equal(assess({ ...valid, documents: [] }).status, "NEEDS_REBUILD");
  assert.equal(assess({ ...valid, expectedIds: [] }).status, "NEEDS_REBUILD");
});
test("缓存标记不能掩盖旧版本、BLOCK 或维度错误", () => {
  assert.equal(
    assess({ ...valid, documents: [{ id: "1", modelVersion: "old" }] }).status,
    "NEEDS_REBUILD",
  );
  assert.equal(
    assess({
      ...valid,
      documents: [{ ...valid.documents[0], verdict: "BLOCK" }],
    }).status,
    "NEEDS_REBUILD",
  );
  assert.equal(assess({ ...valid, dimensions: 1536 }).status, "NEEDS_REBUILD");
});
test("重建进行中与空索引不误报为已完成", () => {
  assert.equal(assess({ ...valid, building: "green" }).status, "BUILDING");
  assert.equal(
    assess({ ...valid, expectedIds: [], documents: [] }).status,
    "EMPTY",
  );
});
