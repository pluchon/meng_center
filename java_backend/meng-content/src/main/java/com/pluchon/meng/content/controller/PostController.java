package com.pluchon.meng.content.controller;

import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.common.core.security.IdentityHeaders;
import com.pluchon.meng.content.controller.dto.CreatePostDTO;
import com.pluchon.meng.content.controller.vo.PostDetailVO;
import com.pluchon.meng.content.controller.vo.PostFeedVO;
import com.pluchon.meng.content.entity.enums.*;
import com.pluchon.meng.content.service.PostDetailService;
import com.pluchon.meng.content.service.PostService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/content/posts")
@Validated
@RequiredArgsConstructor
public class PostController {

    private final PostDetailService details;

    @GetMapping("/{id}")
    public ApiResponse<PostDetailVO> detail(@PathVariable @Positive long id) {
        return ApiResponse.success(details.get(id));
    }

    private final PostService service;

    @PostMapping
    public ApiResponse<Long> create(
        @Valid @RequestBody CreatePostDTO body,
        @RequestHeader(
            value = IdentityHeaders.CLIENT_IP,
            required = false
        ) String ip
    ) {
        return ApiResponse.success(service.create(body, ip));
    }

    @GetMapping
    public ApiResponse<PageResult<PostFeedVO>> feed(
        @RequestParam(required = false) PostCategory category,
        @RequestParam(defaultValue = "LATEST") PostSort sort,
        @RequestParam(defaultValue = "1") @Min(1) long page,
        @RequestParam(defaultValue = "10") @Min(1) @Max(50) long size
    ) {
        return ApiResponse.success(service.feed(category, sort, page, size));
    }

    @PutMapping("/{id}/like")
    public ApiResponse<Void> like(@PathVariable @Positive long id) {
        service.like(id, true);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}/like")
    public ApiResponse<Void> unlike(@PathVariable @Positive long id) {
        service.like(id, false);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/favorite")
    public ApiResponse<Void> favorite(@PathVariable @Positive long id) {
        service.favorite(id, true);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}/favorite")
    public ApiResponse<Void> unfavorite(@PathVariable @Positive long id) {
        service.favorite(id, false);
        return ApiResponse.success(null);
    }
}
