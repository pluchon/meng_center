package com.pluchon.meng.content.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.security.IdentityRole;
import com.pluchon.meng.common.web.context.UserContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// 管理员触发热榜和索引重建
@Service
public class ReadModelAdminService {

    // 搜索索引维护
    @Autowired
    private PostSearchIndex search;

    // 热榜维护
    @Autowired
    private HotRankService hot;

    // 校验管理员身份后提交索引重建
    public void rebuildSearch() {
        requireAdmin();
        search.requestRebuild(false);
    }

    // 校验管理员身份后提交热榜重建
    public void rebuildHot() {
        requireAdmin();
        hot.requestRebuild();
    }

    // 校验网关注入的管理员角色
    private void requireAdmin() {
        if (!IdentityRole.ADMIN.name().equals(UserContext.require().role()))
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }
}
