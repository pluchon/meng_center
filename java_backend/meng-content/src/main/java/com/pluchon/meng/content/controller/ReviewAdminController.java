package com.pluchon.meng.content.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.content.service.AiAdminService;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 管理员内容审核复核接口
@RestController
@RequestMapping("/content/admin/ai/reviews")
public class ReviewAdminController {

    // 对应领域业务操作
    @Autowired
    private AiAdminService service;

    public record Decision(boolean approved) {}

    /** 列出需要复核的内容。 */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.success(service.list());
    }

    /** 提交人工复核结论。 */
    @PostMapping("/{kind}/{id}")
    public ApiResponse<Void> decide(
        @PathVariable String kind,
        @PathVariable long id,
        @RequestBody Decision body
    ) {
        service.decide(kind, id, body.approved());
        return ApiResponse.success(null);
    }
}
