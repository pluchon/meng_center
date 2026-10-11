package com.pluchon.meng.content.controller;

import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.common.core.security.IdentityHeaders;
import com.pluchon.meng.content.controller.dto.CreatePostDTO;
import com.pluchon.meng.content.controller.vo.CreatorPostVO;
import com.pluchon.meng.content.controller.vo.PostDetailVO;
import com.pluchon.meng.content.controller.vo.PostFeedVO;
import com.pluchon.meng.content.entity.enums.PostCategory;
import com.pluchon.meng.content.entity.enums.PostSort;
import com.pluchon.meng.content.service.CreatorService;
import com.pluchon.meng.content.service.PostDetailService;
import com.pluchon.meng.content.service.PostService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
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

// 帖子与互动接口
@RestController
@RequestMapping("/content/posts")
@Validated
public class PostController {

    // 自己发布的帖子查询
    @Autowired
    private CreatorService creator;

    /** 分页查询当前作者自己的帖子。 */
    @GetMapping("/mine")
    public ApiResponse<PageResult<CreatorPostVO>> mine(
        @RequestParam(defaultValue = "1") @Min(1) long page,
        @RequestParam(defaultValue = "10") @Min(1) @Max(50) long size
    ) {
        return ApiResponse.success(creator.mine(page, size));
    }

    // 帖子详情查询
    @Autowired
    private PostDetailService details;

    /** 读取帖子详情并记录浏览。 */
    @GetMapping("/{id}")
    public ApiResponse<PostDetailVO> detail(@PathVariable @Positive long id) {
        return ApiResponse.success(details.get(id));
    }

    // 对应接口的业务操作
    @Autowired
    private PostService service;

    /** 发布新帖子。 */
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

    /** 更新自己的帖子。 */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
        @PathVariable @Positive long id,
        @Valid @RequestBody CreatePostDTO body
    ) {
        service.update(id, body);
        return ApiResponse.success(null);
    }

    /** 删除有权限操作的帖子。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    /** 分页查询帖子流。 */
    @GetMapping
    public ApiResponse<PageResult<PostFeedVO>> feed(
        @RequestParam(required = false) PostCategory category,
        @RequestParam(defaultValue = "LATEST") PostSort sort,
        @RequestParam(defaultValue = "1") @Min(1) long page,
        @RequestParam(defaultValue = "10") @Min(1) @Max(50) long size
    ) {
        return ApiResponse.success(service.feed(category, sort, page, size));
    }

    /** 点赞帖子。 */
    @PutMapping("/{id}/like")
    public ApiResponse<Void> like(@PathVariable @Positive long id) {
        service.like(id, true);
        return ApiResponse.success(null);
    }

    /** 取消帖子点赞。 */
    @DeleteMapping("/{id}/like")
    public ApiResponse<Void> unlike(@PathVariable @Positive long id) {
        service.like(id, false);
        return ApiResponse.success(null);
    }

    /** 收藏帖子。 */
    @PutMapping("/{id}/favorite")
    public ApiResponse<Void> favorite(@PathVariable @Positive long id) {
        service.favorite(id, true);
        return ApiResponse.success(null);
    }

    /** 取消帖子收藏。 */
    @DeleteMapping("/{id}/favorite")
    public ApiResponse<Void> unfavorite(@PathVariable @Positive long id) {
        service.favorite(id, false);
        return ApiResponse.success(null);
    }
}
