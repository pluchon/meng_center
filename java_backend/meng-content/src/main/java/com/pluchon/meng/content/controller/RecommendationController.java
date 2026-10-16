package com.pluchon.meng.content.controller;

import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.content.controller.vo.PostFeedVO;
import com.pluchon.meng.content.service.RecommendationFeedbackService;
import com.pluchon.meng.content.service.RelatedPostService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 推荐反馈、相关帖子与推荐作者接口
@RestController
@RequestMapping("/content")
public class RecommendationController {

    @Autowired
    private RecommendationFeedbackService feedback;

    @Autowired
    private RelatedPostService related;

    public record Feedback(@Positive long postId) {}

    /** 记录当前用户的不感兴趣反馈。 */
    @PostMapping("/recommendations/feedback")
    public ApiResponse<Void> feedback(@Valid @RequestBody Feedback body) {
        feedback.feedback(body.postId());
        return ApiResponse.success(null);
    }

    /** 分页读取相关帖子，总数最多 50 篇。 */
    @GetMapping("/posts/{id}/related")
    public ApiResponse<PageResult<PostFeedVO>> related(
        @PathVariable long id,
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "5") long size
    ) {
        return ApiResponse.success(related.related(id, page, size));
    }

    /** 读取推荐关注的作者。 */
    @GetMapping("/recommendations/authors")
    public ApiResponse<List<Map<String, Object>>> authors(
        @RequestParam(defaultValue = "5") int size
    ) {
        return ApiResponse.success(related.authors(size));
    }
}
