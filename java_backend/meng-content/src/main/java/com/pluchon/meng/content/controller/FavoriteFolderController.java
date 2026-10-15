package com.pluchon.meng.content.controller;

import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.content.controller.dto.FavoriteFoldersDTO;
import com.pluchon.meng.content.entity.enums.FolderColor;
import com.pluchon.meng.content.service.FavoriteFolderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@RequestMapping("/content")
public class FavoriteFolderController {

    @Autowired
    private FavoriteFolderService folders;

    public record Change(
        @NotNull @Size(min = 1, max = 12) String name,
        @NotNull @Size(max = 50) String description,
        // 不传表示不改颜色
        FolderColor color
    ) {}

    @GetMapping("/favorite-folders")
    public ApiResponse<PageResult<FavoriteFolderService.FolderVO>> list(
        @RequestParam(defaultValue = "1") @Min(1) long page,
        @RequestParam(defaultValue = "8") @Min(1) @Max(20) long size
    ) {
        return ApiResponse.success(folders.list(page, size));
    }

    /** 这篇帖子放进了我的哪些收藏夹。 */
    @GetMapping("/posts/{postId}/favorite-folders")
    public ApiResponse<List<Long>> selected(
        @PathVariable @Positive long postId
    ) {
        return ApiResponse.success(folders.selected(postId));
    }

    /** 一次性设定这篇帖子放进哪些收藏夹。 */
    @PutMapping("/posts/{postId}/favorite-folders")
    public ApiResponse<FavoriteFolderService.FavoriteState> replace(
        @PathVariable @Positive long postId,
        @Valid @RequestBody FavoriteFoldersDTO body
    ) {
        return ApiResponse.success(folders.replace(postId, body.folderIds()));
    }

    /** 把帖子放进我的收藏夹。 */
    @PutMapping("/favorite-folders/{folderId}/posts/{postId}")
    public ApiResponse<FavoriteFolderService.FavoriteState> add(
        @PathVariable @Positive long folderId,
        @PathVariable @Positive long postId
    ) {
        return ApiResponse.success(folders.add(folderId, postId));
    }

    /** 把帖子从收藏夹拿走。 */
    @DeleteMapping("/favorite-folders/{folderId}/posts/{postId}")
    public ApiResponse<FavoriteFolderService.FavoriteState> remove(
        @PathVariable @Positive long folderId,
        @PathVariable @Positive long postId
    ) {
        return ApiResponse.success(folders.remove(folderId, postId));
    }

    @PostMapping("/favorite-folders")
    public ApiResponse<Long> create(
        @RequestParam String name,
        @RequestParam(defaultValue = "") String description,
        @RequestParam(required = false) FolderColor color,
        @RequestParam(required = false) MultipartFile cover
    ) throws IOException {
        return ApiResponse.success(
            folders.create(name, description, color, cover)
        );
    }

    @PutMapping("/favorite-folders/{id}")
    public ApiResponse<Void> update(
        @PathVariable long id,
        @Valid @RequestBody Change body
    ) {
        folders.update(id, body.name(), body.description(), body.color());
        return ApiResponse.success(null);
    }

    /** 更换收藏夹的封面，默认收藏夹不能换。 */
    @PostMapping("/favorite-folders/{id}/cover")
    public ApiResponse<Void> changeCover(
        @PathVariable @Positive long id,
        @RequestParam MultipartFile cover
    ) throws IOException {
        folders.changeCover(id, cover);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/favorite-folders/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        folders.delete(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/favorite-folders/{id}/posts")
    public ApiResponse<PageResult<FavoriteFolderService.SavedPost>> posts(
        @PathVariable long id,
        @RequestParam(defaultValue = "1") @Min(1) long page,
        @RequestParam(defaultValue = "8") @Min(1) @Max(20) long size
    ) {
        return ApiResponse.success(folders.saved(id, page, size));
    }

    @GetMapping("/posts/liked")
    public ApiResponse<PageResult<FavoriteFolderService.SavedPost>> likes(
        @RequestParam(defaultValue = "1") @Min(1) long page,
        @RequestParam(defaultValue = "8") @Min(1) @Max(20) long size
    ) {
        return ApiResponse.success(folders.saved(null, page, size));
    }

    @GetMapping("/me/counts")
    public ApiResponse<FavoriteFolderService.Counts> counts() {
        return ApiResponse.success(folders.counts());
    }

    @GetMapping("/folder-covers/{name}")
    public ResponseEntity<Resource> cover(@PathVariable String name) {
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .header("X-Content-Type-Options", "nosniff")
            .body(folders.cover(name));
    }
}
