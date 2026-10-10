package com.pluchon.meng.growth.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.growth.controller.vo.GrowthVO;
import com.pluchon.meng.growth.service.GrowthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/growth")
@RequiredArgsConstructor
public class GrowthController {

    private final GrowthService growth;

    @GetMapping("/me")
    public ApiResponse<GrowthVO> me() {
        return ApiResponse.success(growth.me());
    }

    @PostMapping("/check-ins")
    public ApiResponse<GrowthVO> checkIn() {
        return ApiResponse.success(growth.checkIn());
    }
}
