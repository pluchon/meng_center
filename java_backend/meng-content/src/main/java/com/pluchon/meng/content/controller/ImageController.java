package com.pluchon.meng.content.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.content.controller.vo.ImageVO;
import com.pluchon.meng.content.service.ImageService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

// 帖子和评论配图接口
@RestController
@RequestMapping("/content")
public class ImageController {

    // 帖子配图数据存取
    @Autowired
    private ImageService images;

    /** 上传评论配图。 */
    @PostMapping("/comment-images")
    public ApiResponse<ImageVO> uploadComment(@RequestParam("file") MultipartFile file)
            throws IOException {
        return ApiResponse.success(images.uploadComment(file));
    }

    /** 上传帖子配图。 */
    @PostMapping("/images")
    public ApiResponse<ImageVO> upload(@RequestPart("file") MultipartFile file) throws IOException {
        return ApiResponse.success(images.upload(file));
    }

    /** 读取原图或缩略图资源。 */
    @GetMapping("/files/{name}")
    public ResponseEntity<Resource> read(@PathVariable String name) {
        Resource file = images.read(name);
        MediaType media =
                name.endsWith(".jpg")
                        ? MediaType.IMAGE_JPEG
                        : name.endsWith(".gif")
                                ? MediaType.IMAGE_GIF
                                : name.endsWith(".webp")
                                        ? MediaType.parseMediaType("image/webp")
                                        : MediaType.IMAGE_PNG;
        return ResponseEntity.ok()
                .contentType(media)
                .header("X-Content-Type-Options", "nosniff")
                .body(file);
    }
}
