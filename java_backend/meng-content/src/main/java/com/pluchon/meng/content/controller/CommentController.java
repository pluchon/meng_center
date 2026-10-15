package com.pluchon.meng.content.controller;

import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.common.core.security.IdentityHeaders;
import com.pluchon.meng.content.controller.dto.CreateCommentDTO;
import com.pluchon.meng.content.controller.vo.CommentLocationVO;
import com.pluchon.meng.content.controller.vo.CommentVO;
import com.pluchon.meng.content.controller.vo.MentionCandidateVO;
import com.pluchon.meng.content.service.CommentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 评论与回复接口
@RestController
@RequestMapping("/content")
@Validated
public class CommentController {

    /** 查询可见评论或回复所在的页码。 */
    @GetMapping("/comments/{id}/location")
    public ApiResponse<CommentLocationVO> location(
        @PathVariable @Positive long id,
        @RequestParam(defaultValue = "10") @Min(1) @Max(50) long size
    ) {
        return ApiResponse.success(service.location(id, size));
    }

    // 对应接口的业务操作
    @Autowired
    private CommentService service;

    /** 分页查询帖子一级评论。 */
    @GetMapping("/posts/{id}/comments")
    public ApiResponse<PageResult<CommentVO>> list(
        @PathVariable @Positive long id,
        @RequestParam(defaultValue = "1") @Min(1) long page,
        @RequestParam(defaultValue = "10") @Min(1) @Max(50) long size
    ) {
        return ApiResponse.success(service.list(id, page, size));
    }

    /** 分页查询指定评论的回复。 */
    @GetMapping("/comments/{id}/replies")
    public ApiResponse<PageResult<CommentVO>> replies(
        @PathVariable @Positive long id,
        @RequestParam(defaultValue = "1") @Min(1) long page,
        @RequestParam(defaultValue = "10") @Min(1) @Max(50) long size
    ) {
        return ApiResponse.success(service.replies(id, page, size));
    }

    /** 提交评论或回复。 */
    @PostMapping("/posts/{id}/comments")
    public ApiResponse<CommentVO> create(
        @PathVariable @Positive long id,
        @Valid @RequestBody CreateCommentDTO body,
        @RequestHeader(
            value = IdentityHeaders.CLIENT_IP,
            required = false
        ) String ip
    ) {
        return ApiResponse.success(service.create(id, body, ip));
    }

    /** 删除有权限操作的评论。 */
    @DeleteMapping("/comments/{id}")
    public ApiResponse<Long> delete(@PathVariable @Positive long id) {
        return ApiResponse.success(service.delete(id));
    }

    /** 查询输入 @ 时可以选的人。 */
    @GetMapping("/posts/{id}/mention-candidates")
    public ApiResponse<List<MentionCandidateVO>> mentionCandidates(
        @PathVariable @Positive long id
    ) {
        return ApiResponse.success(service.mentionCandidates(id));
    }

    /** 帖子作者置顶一级评论。 */
    @PutMapping("/comments/{id}/pin")
    public ApiResponse<Void> pin(@PathVariable @Positive long id) {
        service.pin(id, true);
        return ApiResponse.success(null);
    }

    /** 帖子作者取消置顶。 */
    @DeleteMapping("/comments/{id}/pin")
    public ApiResponse<Void> unpin(@PathVariable @Positive long id) {
        service.pin(id, false);
        return ApiResponse.success(null);
    }

    /** 点赞评论。 */
    @PutMapping("/comments/{id}/like")
    public ApiResponse<Void> like(@PathVariable @Positive long id) {
        service.like(id, true);
        return ApiResponse.success(null);
    }

    /** 取消评论点赞。 */
    @DeleteMapping("/comments/{id}/like")
    public ApiResponse<Void> unlike(@PathVariable @Positive long id) {
        service.like(id, false);
        return ApiResponse.success(null);
    }
}
