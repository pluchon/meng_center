package com.pluchon.meng.content.controller;

import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.content.controller.vo.PostFeedVO;
import com.pluchon.meng.content.service.FavoriteFolderService;
import com.pluchon.meng.content.service.PostService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 别人的主页上能看到的内容：他发布的帖子、他公开的收藏夹
@RestController
@Validated
@RequestMapping("/content/users/{userId}")
public class UserContentController {

    @Autowired
    private PostService posts;

    @Autowired
    private FavoriteFolderService folders;

    /** 某人发布的帖子，新的在前。 */
    @GetMapping("/posts")
    public ApiResponse<PageResult<PostFeedVO>> posts(
        @PathVariable @Positive long userId,
        @RequestParam(defaultValue = "1") @Min(1) long page,
        @RequestParam(defaultValue = "10") @Min(1) @Max(50) long size
    ) {
        return ApiResponse.success(posts.byAuthor(userId, page, size));
    }

    /** 某人公开的收藏夹。 */
    @GetMapping("/favorite-folders")
    public ApiResponse<PageResult<FavoriteFolderService.FolderVO>> favoriteFolders(
        @PathVariable @Positive long userId,
        @RequestParam(defaultValue = "1") @Min(1) long page,
        @RequestParam(defaultValue = "8") @Min(1) @Max(20) long size
    ) {
        return ApiResponse.success(folders.publicList(userId, page, size));
    }

    /** 某人一个公开收藏夹里的帖子，私密的收藏夹按不存在处理。 */
    @GetMapping("/favorite-folders/{folderId}/posts")
    public ApiResponse<PageResult<FavoriteFolderService.SavedPost>> folderPosts(
        @PathVariable @Positive long userId,
        @PathVariable @Positive long folderId,
        @RequestParam(defaultValue = "1") @Min(1) long page,
        @RequestParam(defaultValue = "8") @Min(1) @Max(20) long size
    ) {
        return ApiResponse.success(
            folders.publicSaved(userId, folderId, page, size)
        );
    }

    /** 某人主页上的数量：发布的帖子、公开的收藏夹、公开收藏夹里的帖子。 */
    @GetMapping("/counts")
    public ApiResponse<FavoriteFolderService.PublicCounts> counts(
        @PathVariable @Positive long userId
    ) {
        return ApiResponse.success(folders.publicCounts(userId));
    }
}
