package com.pluchon.meng.user.controller;

import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.user.controller.dto.RejectionDTO;
import com.pluchon.meng.user.controller.vo.UserVO;
import com.pluchon.meng.user.entity.enums.UserStatus;
import com.pluchon.meng.user.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(UserRoutes.ROOT)
public final class ReviewController {

    private final ReviewService review;
    private final UserQueryService users;

    public ReviewController(ReviewService review, UserQueryService users) {
        this.review = review;
        this.users = users;
    }

    @GetMapping(UserRoutes.ADMIN_REGISTRATIONS)
    public ApiResponse<PageResult<UserVO>> registrations(
        @RequestParam(defaultValue = UserRoutes.DEFAULT_PAGE) @Min(1) long page,
        @RequestParam(defaultValue = UserRoutes.DEFAULT_SIZE) @Min(1) @Max(100) long size,
        @RequestParam(required = false) UserStatus status
    ) {
        return ApiResponse.success(users.registrations(page, size, status));
    }

    @PostMapping(UserRoutes.APPROVAL)
    public ApiResponse<Void> approve(@PathVariable @Positive long userId) {
        review.approve(userId);
        return ApiResponse.success(null);
    }

    @PostMapping(UserRoutes.REJECTION)
    public ApiResponse<Void> reject(@PathVariable @Positive long userId, @Valid @RequestBody RejectionDTO request) {
        review.reject(userId, request.reason());
        return ApiResponse.success(null);
    }
}
