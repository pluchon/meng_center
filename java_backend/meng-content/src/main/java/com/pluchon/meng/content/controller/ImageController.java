package com.pluchon.meng.content.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.content.controller.vo.ImageVO;
import com.pluchon.meng.content.service.ImageService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/content")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService images;

    @PostMapping("/images")
    public ApiResponse<ImageVO> upload(@RequestPart("file") MultipartFile file)
        throws IOException {
        return ApiResponse.success(images.upload(file));
    }

    @GetMapping("/files/{name}")
    public ResponseEntity<Resource> read(@PathVariable String name) {
        Resource file = images.read(name);
        MediaType media = name.endsWith(".jpg")
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
