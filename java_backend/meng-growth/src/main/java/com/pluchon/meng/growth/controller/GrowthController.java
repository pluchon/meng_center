package com.pluchon.meng.growth.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.growth.controller.vo.GrowthVO;
import com.pluchon.meng.growth.service.GrowthService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 成长与签到接口
@RestController
@RequestMapping("/growth")
public class GrowthController {

    // 成长业务操作
    @Autowired
    private GrowthService growth;

    /** 查询当前用户成长概况。 */
    @GetMapping("/me")
    public ApiResponse<GrowthVO> me() {
        return ApiResponse.success(growth.me());
    }

    /** 提交今日签到。 */
    @PostMapping("/check-ins")
    public ApiResponse<GrowthVO> checkIn() {
        return ApiResponse.success(growth.checkIn());
    }
}
