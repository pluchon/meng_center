package com.pluchon.meng.growth.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.growth.controller.vo.CheckInMonthVO;
import com.pluchon.meng.growth.controller.vo.ExperienceRecordVO;
import com.pluchon.meng.growth.controller.vo.ExperienceStatsVO;
import com.pluchon.meng.growth.controller.vo.GrowthOverviewVO;
import com.pluchon.meng.growth.entity.enums.ExpSource;
import com.pluchon.meng.growth.service.GrowthPageService;
import com.pluchon.meng.growth.controller.vo.GrowthVO;
import com.pluchon.meng.growth.service.GrowthService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

// 成长与签到接口
@RestController
@RequestMapping("/growth")
public class GrowthController {

    // 成长业务操作
    @Autowired
    private GrowthService growth;

    // 成长中心只读查询
    @Autowired
    private GrowthPageService page;

    /** 查询指定月份的签到日历和当前连续签到。 */
    @GetMapping("/check-ins")
    public ApiResponse<CheckInMonthVO> checkIns(@RequestParam(required = false) String month) {
        return ApiResponse.success(page.checkIns(month));
    }

    /** 查询等级门槛与当前用户的今日任务。 */
    @GetMapping("/overview")
    public ApiResponse<GrowthOverviewVO> overview() {
        return ApiResponse.success(page.overview());
    }

    /** 查询连续七天或三十天的经验统计。 */
    @GetMapping("/exp-stats")
    public ApiResponse<ExperienceStatsVO> stats(@RequestParam(defaultValue = "7") int days) {
        return ApiResponse.success(page.stats(days));
    }

    /** 分页查询自己的经验明细并可按来源筛选。 */
    @GetMapping("/exp-records")
    public ApiResponse<PageResult<ExperienceRecordVO>> records(
            @RequestParam(required = false) ExpSource source,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "12") long size) {
        return ApiResponse.success(this.page.records(source, page, size));
    }

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
