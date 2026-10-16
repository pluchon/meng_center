package com.pluchon.meng.content.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.content.controller.dto.AiDraftDTO;
import com.pluchon.meng.content.controller.vo.ImageVO;
import com.pluchon.meng.content.service.ContentAssistantService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 发帖辅助与创作复盘接口
@RestController
@RequestMapping("/content")
public class ContentAiController {

    // AI 辅助业务入口
    @Autowired
    private ContentAssistantService assistant;

    /** 为当前草稿挑选标签。 */
    @PostMapping("/ai/tag-suggestions")
    public ApiResponse<Map<String, Object>> tags(@Valid @RequestBody AiDraftDTO body) {
        return ApiResponse.success(assistant.tags(body));
    }

    /** 生成可选择的写作灵感。 */
    @PostMapping("/ai/inspirations")
    public ApiResponse<Map<String, Object>> inspirations(@Valid @RequestBody AiDraftDTO body) {
        return ApiResponse.success(assistant.inspirations(body));
    }

    /** 生成并保存当前草稿封面。 */
    @PostMapping("/ai/covers")
    public ApiResponse<ImageVO> cover(@Valid @RequestBody AiDraftDTO body) {
        return ApiResponse.success(assistant.cover(body));
    }

    public record Period(int days) {}

    /** 生成当前用户的创作复盘。 */
    @PostMapping("/creator/ai-reviews")
    public ApiResponse<Map<String, Object>> review(@RequestBody Period body) {
        return ApiResponse.success(assistant.review(body.days()));
    }
}
