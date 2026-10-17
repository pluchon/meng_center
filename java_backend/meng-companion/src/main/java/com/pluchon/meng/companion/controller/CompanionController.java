package com.pluchon.meng.companion.controller;

import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.companion.service.CompanionLimits;
import com.pluchon.meng.companion.service.CompanionService;
import com.pluchon.meng.companion.service.PrivateImages;
import com.pluchon.meng.companion.service.ReplyService;
import com.pluchon.meng.companion.service.WorkStorage;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;

// 萌萌所有对外入口都要求网关注入身份
@RestController
@RequestMapping("/companion")
public class CompanionController {

    @Autowired
    private CompanionService service;

    @Autowired
    private ReplyService replies;

    @Autowired
    private CompanionLimits limits;

    @Autowired
    private WorkStorage storage;

    @Autowired
    private PrivateImages images;

    /** 返回角色卡及当前用户的关系投影。 */
    @GetMapping("/characters")
    public ApiResponse<List<CompanionVO.Character>> characters() {
        return ApiResponse.success(service.characters(user()));
    }

    /** 查询当前角色的阶段之路和相识信息。 */
    @GetMapping("/characters/{code}")
    public ApiResponse<CompanionVO.Character> character(
        @PathVariable String code
    ) {
        return ApiResponse.success(service.detail(user(), code));
    }

    /** 分页读取本人角色会话。 */
    @GetMapping("/characters/{code}/sessions")
    public ApiResponse<PageResult<CompanionVO.Session>> sessions(
        @PathVariable String code,
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(service.sessions(user(), code, page, size));
    }

    /** 创建会话并保存当前阶段的开场白。 */
    @PostMapping("/characters/{code}/sessions")
    public ApiResponse<CompanionVO.Session> create(@PathVariable String code) {
        return ApiResponse.success(service.createSession(user(), code));
    }

    /** 逻辑删除本人会话。 */
    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> delete(
        @PathVariable long id,
        @RequestParam(defaultValue = "false") boolean forget
    ) {
        service.deleteSession(user(), id, forget);
        return ApiResponse.success(null);
    }

    /** 以消息编号为游标向前读取历史。 */
    @GetMapping("/sessions/{id}/messages")
    public ApiResponse<CompanionVO.Messages> messages(
        @PathVariable long id,
        @RequestParam(required = false) Long before,
        @RequestParam(defaultValue = "30") int size
    ) {
        return ApiResponse.success(service.messages(user(), id, before, size));
    }

    /** 按审核后的完整气泡输出本轮结果。 */
    @PostMapping(value = "/sessions/{id}/messages")
    public DeferredResult<ResponseEntity<?>> send(
        @PathVariable long id,
        @Valid @RequestBody CompanionDTO.Send body,
        @RequestHeader(value = "X-Time-Zone", required = false) String zone,
        @RequestHeader(
            value = "X-Client-IP",
            defaultValue = "unknown"
        ) String ip
    ) {
        return replies.send(user(), id, body, zone, ip);
    }

    /** 返回当前角色对本人的长期记忆。 */
    @GetMapping("/characters/{code}/memories")
    public ApiResponse<List<CompanionVO.Memory>> memories(
        @PathVariable String code
    ) {
        return ApiResponse.success(service.memories(user(), code));
    }

    /** 删除一条本人记忆并使正在执行的旧上下文失效。 */
    @DeleteMapping("/memories/{id}")
    public ApiResponse<Void> deleteMemory(@PathVariable long id) {
        service.deleteMemory(user(), id);
        return ApiResponse.success(null);
    }

    /** 清空这个角色对本人的长期记忆。 */
    @DeleteMapping("/characters/{code}/memories")
    public ApiResponse<Void> clear(@PathVariable String code) {
        service.clearMemories(user(), code);
        return ApiResponse.success(null);
    }

    /** 更新性格浓度，下一轮生成自然回应。 */
    @PutMapping("/characters/{code}/level")
    public ApiResponse<Void> level(
        @PathVariable String code,
        @Valid @RequestBody CompanionDTO.Level body,
        @RequestHeader(value = "X-Time-Zone", required = false) String zone
    ) {
        service.level(user(), code, body.level(), zone);
        return ApiResponse.success(null);
    }

    /** 编辑和置顶本人有效记忆。 */
    @PatchMapping("/memories/{id}")
    public ApiResponse<Void> editMemory(
        @PathVariable long id,
        @Valid @RequestBody CompanionDTO.Memory body
    ) {
        service.editMemory(user(), id, body);
        return ApiResponse.success(null);
    }

    /** 重写这一轮一次，并记录被替换的回复。 */
    @PostMapping("/messages/{id}/regeneration")
    public DeferredResult<ResponseEntity<?>> regenerate(
        @PathVariable long id,
        @RequestHeader(value = "X-Time-Zone", required = false) String zone,
        @RequestHeader(
            value = "X-Client-IP",
            defaultValue = "unknown"
        ) String ip
    ) {
        return replies.regenerate(user(), id, zone, ip);
    }

    /** 反馈只引用本人气泡，不接收客户端伪造的对话内容。 */
    @PostMapping("/messages/{id}/feedback")
    public ApiResponse<Void> feedback(@PathVariable long id) {
        replies.feedback(user(), id);
        return ApiResponse.success(null);
    }

    /** 导出全天约定，日期只使用用户的当地日期。 */
    @GetMapping("/memories/{id}/calendar")
    public ResponseEntity<byte[]> calendar(@PathVariable long id) {
        return ResponseEntity.ok()
            .contentType(
                MediaType.parseMediaType("text/calendar;charset=UTF-8")
            )
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=promise.ics"
            )
            .body(
                service.calendar(user(), id).getBytes(StandardCharsets.UTF_8)
            );
    }

    /** 重置关系并清除长期记忆及旧会话上下文。 */
    @PostMapping("/characters/{code}/reset")
    public ApiResponse<Void> reset(@PathVariable String code) {
        service.reset(user(), code);
        return ApiResponse.success(null);
    }

    /** 分页读取本人作品。 */
    @GetMapping("/works")
    public ApiResponse<PageResult<CompanionVO.Work>> works(
        @RequestParam(required = false) String character,
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(
            service.works(user(), character, page, size)
        );
    }

    /** 读取作品正文或图片地址。 */
    @GetMapping("/works/{id}")
    public ApiResponse<CompanionVO.Work> work(@PathVariable long id) {
        return ApiResponse.success(service.work(user(), id));
    }

    /** 下载文档或重新编码后的原尺寸画作。 */
    @GetMapping("/works/{id}/file")
    public ResponseEntity<?> file(@PathVariable long id) {
        var work = service.requireWork(user(), id);
        boolean document = "DOCUMENT".equals(work.getKind());
        Object body = document
            ? work.getBody().getBytes(StandardCharsets.UTF_8)
            : new FileSystemResource(storage.path(work.getFilePath()));
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header("X-Content-Type-Options", "nosniff")
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                    .filename(
                        work.getTitle() + (document ? ".md" : ".png"),
                        StandardCharsets.UTF_8
                    )
                    .build()
                    .toString()
            )
            .contentType(
                document
                    ? MediaType.parseMediaType("text/markdown;charset=UTF-8")
                    : MediaType.IMAGE_PNG
            )
            .body(body);
    }

    /** 逻辑删除本人作品。 */
    @DeleteMapping("/works/{id}")
    public ApiResponse<Void> deleteWork(@PathVariable long id) {
        service.deleteWork(user(), id);
        return ApiResponse.success(null);
    }

    /** 查询今日剩余次数和累计聊天时间。 */
    @GetMapping("/usage")
    public ApiResponse<Map<String, Object>> usage(
        @RequestHeader(value = "X-Time-Zone", required = false) String zone
    ) {
        return ApiResponse.success(
            limits.usage(
                user(),
                com.pluchon.meng.companion.service.CompanionContext.zone(zone)
            )
        );
    }

    /** 上传私密对话配图，发送时按评论配图规则审核。 */
    @PostMapping("/images")
    public ApiResponse<Map<String, Object>> upload(
        @RequestParam MultipartFile file
    ) throws IOException {
        return ApiResponse.success(images.upload(user(), file));
    }

    /** 只允许上传者读取私密图片。 */
    @GetMapping("/images/{id}")
    public ResponseEntity<FileSystemResource> image(@PathVariable long id) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header("X-Content-Type-Options", "nosniff")
            .contentType(MediaType.IMAGE_PNG)
            .body(new FileSystemResource(images.read(user(), id)));
    }

    private long user() {
        return UserContext.require().userId();
    }
}
