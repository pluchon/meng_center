package com.pluchon.meng.user.controller;

import com.pluchon.meng.api.user.contract.UserQueryContract;
import com.pluchon.meng.api.user.vo.PublicUserVO;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.user.service.UserQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class InternalUserController implements UserQueryContract {

    private final UserQueryService users;

    public InternalUserController(UserQueryService users) {
        this.users = users;
    }

    public ApiResponse<List<PublicUserVO>> users(List<Long> ids) {
        return ApiResponse.success(users.users(ids));
    }
}
