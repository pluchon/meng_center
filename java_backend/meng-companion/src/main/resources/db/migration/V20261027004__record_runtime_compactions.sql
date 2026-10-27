CREATE TABLE companion_runtime_daily (
    day_utc DATE NOT NULL PRIMARY KEY,
    idle_compactions BIGINT NOT NULL DEFAULT 0,
    capacity_compactions BIGINT NOT NULL DEFAULT 0
) COMMENT='奶糖运行时按 UTC 自然日聚合的压缩次数，不保存用户或正文';
