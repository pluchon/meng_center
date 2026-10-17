package com.pluchon.meng.growth.controller;

import com.pluchon.meng.api.growth.contract.CompanionGrowthContract;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.growth.service.CompanionGrowthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

// 萌萌的成长工具入口，只返回已经存在的业务事实
@RestController
public class CompanionGrowthController implements CompanionGrowthContract {
    @Autowired
    private CompanionGrowthService service;

    /** 查询用户等级及连续签到状态。 */
    @Override
    public ApiResponse<Summary> summary(long id) { return ApiResponse.success(service.summary(id)); }
}
