package com.pluchon.meng.user.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.common.core.security.IdentityHeaders;
import com.pluchon.meng.user.controller.dto.ContactChangeDTO;
import com.pluchon.meng.user.controller.dto.ProfileDTO;
import com.pluchon.meng.user.service.AvatarService;
import com.pluchon.meng.user.service.CodeDeliveryService;
import com.pluchon.meng.user.service.ProfileModerationService;
import com.pluchon.meng.user.service.ProfileService;
import com.pluchon.meng.user.service.security.AuthorizationService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/user")
public class ProfileController {

    @Autowired
    private ProfileModerationService moderation;

    @GetMapping("/admin/profile-reviews")
    public ApiResponse<java.util.List<java.util.Map<String, Object>>> profileReviews() {
        return ApiResponse.success(moderation.pending());
    }

    @GetMapping("/me/profile-review")
    public ApiResponse<java.util.Map<String, Object>> review() {
        return ApiResponse.success(moderation.latest());
    }

    @PostMapping("/admin/profile-reviews/{id}/restore")
    public ApiResponse<Void> restore(@PathVariable long id) {
        moderation.restore(id);
        return ApiResponse.success(null);
    }

    @Autowired
    private ProfileService profiles;

    @Autowired
    private AvatarService avatars;

    @Autowired
    private CodeDeliveryService delivery;

    @Autowired
    private AuthorizationService authorization;

    @PutMapping("/me/profile")
    public ApiResponse<Void> profile(@Valid @RequestBody ProfileDTO body) {
        profiles.update(body);
        return ApiResponse.success(null);
    }

    @PostMapping("/me/avatar")
    public ApiResponse<String> avatar(@RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.success(avatars.upload(file));
    }

    @GetMapping("/avatars/{name}")
    public ResponseEntity<Resource> avatar(@PathVariable String name) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("image/webp"))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
            .header("X-Content-Type-Options", "nosniff")
            .body(avatars.read(name));
    }

    @PostMapping("/me/contact-codes")
    public ApiResponse<Void> codes(
        @Valid @RequestBody ContactChangeDTO body,
        @RequestHeader(IdentityHeaders.CLIENT_IP) String ip
    ) {
        authorization.current();
        delivery.contactCode(body.email(), body.countryCode(), body.phone(), body.ticket(), ip);
        return ApiResponse.success(null);
    }

    @PutMapping("/me/email")
    public ApiResponse<Void> email(@Valid @RequestBody ContactChangeDTO body) {
        profiles.contact(body, true);
        return ApiResponse.success(null);
    }

    @PutMapping("/me/phone")
    public ApiResponse<Void> phone(@Valid @RequestBody ContactChangeDTO body) {
        profiles.contact(body, false);
        return ApiResponse.success(null);
    }
}
