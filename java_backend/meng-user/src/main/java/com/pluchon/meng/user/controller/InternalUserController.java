package com.pluchon.meng.user.controller;

import com.pluchon.meng.api.user.contract.UserQueryContract;
import com.pluchon.meng.api.user.vo.PublicUserVO;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.user.service.UserQueryService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

// 给其他服务调用的内部接口，路径由契约定义，网关不对外放行
@RestController
public final class InternalUserController implements UserQueryContract {

    @Autowired
    private UserQueryService users;

    /** 按编号批量查询公开资料 */
    @Override
    public ApiResponse<List<PublicUserVO>> users(List<Long> ids) {
        return ApiResponse.success(users.users(ids));
    }
}
