package com.pluchon.meng.content.controller;

import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.common.core.security.IdentityHeaders;
import com.pluchon.meng.content.controller.dto.CreateCommentDTO;
import com.pluchon.meng.content.controller.vo.CommentVO;
import com.pluchon.meng.content.service.CommentService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/content")
@Validated
@RequiredArgsConstructor
public class CommentController {
    private final CommentService service;

    @GetMapping("/posts/{id}/comments")
    public ApiResponse<PageResult<CommentVO>> list(
            @PathVariable @Positive long id,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) long size) {
        return ApiResponse.success(service.list(id, page, size));
    }

    @GetMapping("/comments/{id}/replies")
    public ApiResponse<PageResult<CommentVO>> replies(
            @PathVariable @Positive long id,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) long size) {
        return ApiResponse.success(service.replies(id, page, size));
    }

    @PostMapping("/posts/{id}/comments")
    public ApiResponse<CommentVO> create(
            @PathVariable @Positive long id,
            @Valid @RequestBody CreateCommentDTO body,
            @RequestHeader(value = IdentityHeaders.CLIENT_IP, required = false) String ip) {
        return ApiResponse.success(service.create(id, body, ip));
    }

    @DeleteMapping("/comments/{id}")
    public ApiResponse<Long> delete(@PathVariable @Positive long id) {
        return ApiResponse.success(service.delete(id));
    }

    @PutMapping("/comments/{id}/like")
    public ApiResponse<Void> like(@PathVariable @Positive long id) {
        service.like(id, true);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/comments/{id}/like")
    public ApiResponse<Void> unlike(@PathVariable @Positive long id) {
        service.like(id, false);
        return ApiResponse.success(null);
    }
}
