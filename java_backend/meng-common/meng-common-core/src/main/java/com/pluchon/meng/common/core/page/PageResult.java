package com.pluchon.meng.common.core.page;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import java.util.List;

// 分页结果，页码和总数由拥有列表的服务给出
public record PageResult<T>(List<T> records, long total, long page, long size) {
    public PageResult {
        records = List.copyOf(records);
        if (total < 0 || page < 1 || size < 1) {
            throw new IllegalArgumentException("Invalid pagination metadata");
        }
    }

    // 页码换算成查询偏移；页码或每页条数不合法、乘积溢出时按参数错误拒绝
    public static long offset(long page, long size) {
        if (page < 1 || size < 1 || page - 1 > Long.MAX_VALUE / size) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        }
        return (page - 1) * size;
    }
}
