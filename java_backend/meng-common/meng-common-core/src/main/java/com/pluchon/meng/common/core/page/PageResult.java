package com.pluchon.meng.common.core.page;

import java.util.List;

public record PageResult<T>(List<T> records, long total, long page, long size) {
    public PageResult {
        records = List.copyOf(records);
        if (total < 0 || page < 1 || size < 1) {
            throw new IllegalArgumentException("Invalid pagination metadata");
        }
    }
}
