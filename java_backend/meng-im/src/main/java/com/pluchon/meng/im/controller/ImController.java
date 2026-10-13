package com.pluchon.meng.im.controller;

import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.im.entity.Channel;
import com.pluchon.meng.im.service.ImService;
import com.pluchon.meng.im.service.PrivateImageService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// 消息域 HTTP 业务入口
@RestController
@RequestMapping("/im")
public class ImController {

    @Autowired
    private ImService im;

    @Autowired
    private PrivateImageService images;

    /** 查询四个频道的未读数。 */
    @GetMapping("/unread")
    public ApiResponse<ImVO.Unread> unread() {
        return ApiResponse.success(im.unread(ImService.user()));
    }

    /** 分页读取自己的会话。 */
    @GetMapping("/conversations")
    public ApiResponse<PageResult<ImVO.Conversation>> conversations(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "30") int size
    ) {
        return ApiResponse.success(im.conversations(page, size));
    }

    /** 打开对方的会话，未发送时不建立记录。 */
    @GetMapping("/conversations/peer/{userId}")
    public ApiResponse<ImVO.Conversation> peer(@PathVariable long userId) {
        return ApiResponse.success(im.peer(userId));
    }

    /** 按游标读取可见历史消息。 */
    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<ImVO.Messages> messages(
        @PathVariable long id,
        @RequestParam(required = false) Long beforeId,
        @RequestParam(defaultValue = "30") int size
    ) {
        return ApiResponse.success(im.messages(id, beforeId, size));
    }

    /** 幂等发送一条文字或图片消息。 */
    @PostMapping("/messages")
    public ApiResponse<ImVO.Message> send(@Valid @RequestBody ImDTO.Send body) {
        return ApiResponse.success(im.send(body));
    }

    /** 标记当前会话的已读游标。 */
    @PutMapping("/conversations/{id}/read")
    public ApiResponse<Void> read(
        @PathVariable long id,
        @Valid @RequestBody ImDTO.Read body
    ) {
        im.read(id, body.messageId());
        return ApiResponse.success(null);
    }

    /** 只对当前用户隐藏会话及历史。 */
    @DeleteMapping("/conversations/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        im.delete(id);
        return ApiResponse.success(null);
    }

    /** 屏蔽指定用户。 */
    @PostMapping("/blocks")
    public ApiResponse<Void> block(@Valid @RequestBody ImDTO.Block body) {
        im.block(body.userId(), true);
        return ApiResponse.success(null);
    }

    @GetMapping("/blocks")
    public ApiResponse<PageResult<ImService.BlockedPeer>> blocks(
        @RequestParam(defaultValue = "1") @jakarta.validation.constraints.Min(
            1
        ) long page,
        @RequestParam(defaultValue = "20") @jakarta.validation.constraints.Min(
            1
        ) @jakarta.validation.constraints.Max(50) long size
    ) {
        return ApiResponse.success(im.blocks(page, size));
    }

    /** 取消屏蔽。 */
    @DeleteMapping("/blocks/{userId}")
    public ApiResponse<Void> unblock(@PathVariable long userId) {
        im.block(userId, false);
        return ApiResponse.success(null);
    }

    /** 上传一张私信图片。 */
    @PostMapping("/images")
    public ApiResponse<ImVO.Image> upload(@RequestParam MultipartFile file)
        throws IOException {
        return ApiResponse.success(images.upload(file));
    }

    /** 鉴权读取原图或缩略图。 */
    @GetMapping("/images/{id}")
    public ResponseEntity<FileSystemResource> image(
        @PathVariable long id,
        @RequestParam(defaultValue = "false") boolean thumb
    ) throws IOException {
        var file = images.read(id, thumb);
        String type = Files.probeContentType(file);
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header("X-Content-Type-Options", "nosniff")
            .contentType(
                MediaType.parseMediaType(
                    type == null ? "application/octet-stream" : type
                )
            )
            .body(new FileSystemResource(file));
    }

    /** 查询在线状态设置。 */
    @GetMapping("/settings")
    public ApiResponse<ImVO.Setting> setting() {
        return ApiResponse.success(im.setting(ImService.user()));
    }

    /** 修改自动在线或隐身设置。 */
    @PutMapping("/settings")
    public ApiResponse<ImVO.Setting> setting(@RequestBody ImDTO.Setting body) {
        return ApiResponse.success(im.setting(body.hidden()));
    }

    /** 分页读取结构化通知，喜欢按目标及动作聚合。 */
    @GetMapping("/notifications")
    public ApiResponse<PageResult<ImVO.Notification>> notifications(
        @RequestParam Channel channel,
        @RequestParam(required = false) String source,
        @RequestParam(required = false) String category,
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(
            im.notifications(channel, source, category, page, size)
        );
    }

    /** 查询左栏来源或系统类型分组。 */
    @GetMapping("/notification-groups")
    public ApiResponse<List<ImVO.Group>> groups(@RequestParam Channel channel) {
        return ApiResponse.success(im.groups(channel));
    }

    /** 将指定通知分组整体标成已读。 */
    @PutMapping("/notifications/read")
    public ApiResponse<Void> read(@Valid @RequestBody ImDTO.NoticeRead body) {
        im.readNotifications(body);
        return ApiResponse.success(null);
    }
}
