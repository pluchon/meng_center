package com.pluchon.meng.common.core.page;

import java.util.List;

// 分页结果，页码和总数由拥有列表的服务给出
public record PageResult<T>(List<T> records, long total, long page, long size) {
    public PageResult {
        records = List.copyOf(records);
        if (total < 0 || page < 1 || size < 1) {
            throw new IllegalArgumentException("Invalid pagination metadata");
        }
    }
}
