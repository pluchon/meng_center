package com.pluchon.meng.companion.controller;

import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.companion.service.CompanionContext;
import com.pluchon.meng.companion.service.CompanionLimits;
import com.pluchon.meng.companion.service.CompanionService;
import com.pluchon.meng.companion.service.PrivateImages;
import com.pluchon.meng.companion.service.ReplyService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;

// 所有入口都要求网关注入身份。
@RestController
@RequestMapping("/companion")
public class CompanionController {
    @Autowired private CompanionService service;
    @Autowired private ReplyService replies;
    @Autowired private CompanionLimits limits;
    @Autowired private PrivateImages images;

    /** 分页读取本人的对话。 */
    @GetMapping("/sessions")
    public ApiResponse<PageResult<CompanionVO.Session>> sessions(
        @RequestParam(defaultValue="1") long page, @RequestParam(defaultValue="20") int size) {
        return ApiResponse.success(service.sessions(user(), page, size));
    }
    /** 新建对话，使用角色卡里的固定开场白。 */
    @PostMapping("/sessions")
    public ApiResponse<CompanionVO.Session> create(
        @RequestHeader(value="X-Time-Zone",required=false) String zone) {
        return ApiResponse.success(service.createSession(user(), CompanionContext.zone(zone)));
    }
    /** 逻辑删除对话，保留长期记忆。 */
    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.deleteSession(user(), id);
        return ApiResponse.success(null);
    }
    /** 以消息编号为游标读取历史。 */
    @GetMapping("/sessions/{id}/messages")
    public ApiResponse<CompanionVO.Messages> messages(@PathVariable long id,
        @RequestParam(required=false) Long before, @RequestParam(defaultValue="30") int size) {
        return ApiResponse.success(service.messages(user(), id, before, size));
    }
    /** 按审核后的完整气泡输出本轮结果。 */
    @PostMapping("/sessions/{id}/messages")
    public DeferredResult<ResponseEntity<?>> send(@PathVariable long id, @Valid @RequestBody CompanionDTO.Send body,
        @RequestHeader(value="X-Time-Zone",required=false) String zone,
        @RequestHeader(value="X-Client-IP",defaultValue="unknown") String ip) {
        return replies.send(user(), id, body, zone, ip);
    }
    /** 清空本人记忆，并使在途的旧整理结果失效。 */
    @DeleteMapping("/memories")
    public ApiResponse<Void> clear() {
        service.clearMemories(user());
        return ApiResponse.success(null);
    }
    /** 返回今日剩余次数和冷却秒数。 */
    @GetMapping("/usage")
    public ApiResponse<Map<String,Object>> usage(@RequestHeader(value="X-Time-Zone",required=false) String zone) {
        return ApiResponse.success(limits.usage(user(), CompanionContext.zone(zone)));
    }
    /** 上传私人对话图片。 */
    @PostMapping("/images")
    public ApiResponse<Map<String,Object>> upload(@RequestParam MultipartFile file) throws IOException {
        return ApiResponse.success(images.upload(user(), file));
    }
    /** 只有上传者能读取图片。 */
    @GetMapping("/images/{id}")
    public ResponseEntity<FileSystemResource> image(@PathVariable long id) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, no-store")
            .header("X-Content-Type-Options","nosniff").contentType(MediaType.IMAGE_PNG)
            .body(new FileSystemResource(images.read(user(),id)));
    }
    private long user() { return UserContext.require().userId(); }
}
