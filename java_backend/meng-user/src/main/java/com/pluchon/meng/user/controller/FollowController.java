package com.pluchon.meng.user.controller;

import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.user.controller.vo.FollowUserVO;
import com.pluchon.meng.user.controller.vo.UserProfileVO;
import com.pluchon.meng.user.service.FollowService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

// 关注关系与别人的主页资料
@RestController
@Validated
@RequestMapping("/user/users")
public class FollowController {

    @Autowired
    private FollowService follows;

    /** 查看别人的主页资料。 */
    @GetMapping("/{id}")
    public ApiResponse<UserProfileVO> profile(@PathVariable @Positive long id) {
        return ApiResponse.success(follows.profile(id));
    }

    /** 关注一个人，重复关注不报错。 */
    @PutMapping("/{id}/follow")
    public ApiResponse<Void> follow(@PathVariable @Positive long id) {
        follows.follow(id);
        return ApiResponse.success(null);
    }

    /** 取消关注，没关注过也不报错。 */
    @DeleteMapping("/{id}/follow")
    public ApiResponse<Void> unfollow(@PathVariable @Positive long id) {
        follows.unfollow(id);
        return ApiResponse.success(null);
    }

    /** 某人关注的人，分页。 */
    @GetMapping("/{id}/following")
    public ApiResponse<PageResult<FollowUserVO>> following(
        @PathVariable @Positive long id,
        @RequestParam(defaultValue = "1") @Min(1) long page,
        @RequestParam(defaultValue = "12") @Min(1) @Max(50) long size
    ) {
        return ApiResponse.success(follows.following(id, page, size));
    }

    /** 某人的粉丝，分页。 */
    @GetMapping("/{id}/followers")
    public ApiResponse<PageResult<FollowUserVO>> followers(
        @PathVariable @Positive long id,
        @RequestParam(defaultValue = "1") @Min(1) long page,
        @RequestParam(defaultValue = "12") @Min(1) @Max(50) long size
    ) {
        return ApiResponse.success(follows.followers(id, page, size));
    }
}
