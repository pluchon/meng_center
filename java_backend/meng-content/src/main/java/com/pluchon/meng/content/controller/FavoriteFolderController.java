package com.pluchon.meng.content.controller;

import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.content.service.FavoriteFolderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
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
        @NotNull @Size(max = 50) String description
    ) {}

    @GetMapping("/favorite-folders")
    public ApiResponse<PageResult<FavoriteFolderService.FolderVO>> list(
        @RequestParam(defaultValue = "1") @Min(1) long page,
        @RequestParam(defaultValue = "8") @Min(1) @Max(20) long size
    ) {
        return ApiResponse.success(folders.list(page, size));
    }

    @PostMapping("/favorite-folders")
    public ApiResponse<Long> create(
        @RequestParam String name,
        @RequestParam(defaultValue = "") String description,
        @RequestParam(required = false) MultipartFile cover
    ) throws IOException {
        return ApiResponse.success(folders.create(name, description, cover));
    }

    @PutMapping("/favorite-folders/{id}")
    public ApiResponse<Void> update(
        @PathVariable long id,
        @Valid @RequestBody Change body
    ) {
        folders.update(id, body.name(), body.description());
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
