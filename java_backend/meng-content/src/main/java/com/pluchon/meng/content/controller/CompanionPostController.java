package com.pluchon.meng.content.controller;

import com.pluchon.meng.api.content.contract.CompanionPostContract;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.content.service.CompanionPostService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

// 网关封锁 internal 路径，只允许本机 AI 工具调用
@RestController
public class CompanionPostController implements CompanionPostContract {

    @Autowired
    private CompanionPostService service;

    /** 向量召回公开候选，由 AI agent 判断相关性。 */
    @Override
    public ApiResponse<List<Post>> vectorSearch(VectorQuery query) {
        return ApiResponse.success(service.vectorSearch(query));
    }

    /** 读取当前工具身份最近的公开帖子。 */
    @Override
    public ApiResponse<List<Post>> recent(long id, int size) {
        return ApiResponse.success(service.recent(id, size));
    }
    @Override public ApiResponse<Detail> read(long id) { return ApiResponse.success(service.read(id)); }
}
