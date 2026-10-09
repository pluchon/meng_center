package com.pluchon.meng.user.controller;

import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.user.controller.dto.RejectionDTO;
import com.pluchon.meng.user.controller.vo.UserVO;
import com.pluchon.meng.user.entity.enums.UserStatus;
import com.pluchon.meng.user.service.ReviewService;
import com.pluchon.meng.user.service.UserQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 管理员审核接口
@RestController
@RequestMapping(UserRoutes.ROOT)
public final class ReviewController {

    @Autowired
    private ReviewService review;

    @Autowired
    private UserQueryService users;

    /** 分页查询注册申请 */
    @GetMapping(UserRoutes.ADMIN_REGISTRATIONS)
    public ApiResponse<PageResult<UserVO>> registrations(
        @RequestParam(defaultValue = UserRoutes.DEFAULT_PAGE) @Min(1) long page,
        @RequestParam(defaultValue = UserRoutes.DEFAULT_SIZE) @Min(1) @Max(UserRoutes.MAX_SIZE) long size,
        @RequestParam(required = false) UserStatus status
    ) {
        return ApiResponse.success(users.registrations(page, size, status));
    }

    /** 通过注册申请 */
    @PostMapping(UserRoutes.APPROVAL)
    public ApiResponse<Void> approve(@PathVariable @Positive long userId) {
        review.approve(userId);
        return ApiResponse.success(null);
    }

    /** 拒绝注册申请 */
    @PostMapping(UserRoutes.REJECTION)
    public ApiResponse<Void> reject(@PathVariable @Positive long userId, @Valid @RequestBody RejectionDTO request) {
        review.reject(userId, request.reason());
        return ApiResponse.success(null);
    }
}
