package com.pluchon.meng.api.growth.contract;

import com.pluchon.meng.common.core.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// 当前用户的成长只读投影，不按需建账户，不发事件
public interface CompanionGrowthContract {
    record Summary(int level, int consecutiveDays, boolean checkedInToday) {}

    @GetMapping("/growth/internal/users/{id}/summary")
    ApiResponse<Summary> summary(@PathVariable long id);
}
