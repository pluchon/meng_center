-- 向量版本包含模型名、维度和编码版本，旧的简写由补向量任务重新计算后替换。
ALTER TABLE companion_memory MODIFY embedding_version VARCHAR(160);
