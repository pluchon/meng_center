package com.pluchon.meng.content.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.content.service.ReadModelAdminService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 管理员重建接口
@RestController
@RequestMapping("/content/admin")
public class ReadModelAdminController {

    // 对应接口的业务操作
    @Autowired
    private ReadModelAdminService service;

    /** 管理员提交搜索索引重建任务。 */
    @PostMapping("/search/rebuild")
    public ApiResponse<Void> search() {
        service.rebuildSearch();
        return ApiResponse.success(null);
    }

    /** 管理员提交热门排行重建任务。 */
    @PostMapping("/hot/rebuild")
    public ApiResponse<Void> hot() {
        service.rebuildHot();
        return ApiResponse.success(null);
    }
}
