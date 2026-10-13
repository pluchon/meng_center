package com.pluchon.meng.user.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.common.core.security.IdentityHeaders;
import com.pluchon.meng.user.controller.dto.ContactChangeDTO;
import com.pluchon.meng.user.controller.dto.ProfileDTO;
import com.pluchon.meng.user.service.*;
import com.pluchon.meng.user.service.security.AuthorizationService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/user")
public class ProfileController {

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
    public ApiResponse<String> avatar(@RequestParam("file") MultipartFile file)
        throws IOException {
        return ApiResponse.success(avatars.upload(file));
    }

    @GetMapping("/avatars/{name}")
    public ResponseEntity<Resource> avatar(@PathVariable String name) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("image/webp"))
            .cacheControl(
                CacheControl.maxAge(Duration.ofDays(365))
                    .cachePrivate()
                    .immutable()
            )
            .header("X-Content-Type-Options", "nosniff")
            .body(avatars.read(name));
    }

    @PostMapping("/me/contact-codes")
    public ApiResponse<Void> codes(
        @Valid @RequestBody ContactChangeDTO body,
        @RequestHeader(IdentityHeaders.CLIENT_IP) String ip
    ) {
        authorization.current();
        delivery.contactCode(
            body.email(),
            body.countryCode(),
            body.phone(),
            body.ticket(),
            ip
        );
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
