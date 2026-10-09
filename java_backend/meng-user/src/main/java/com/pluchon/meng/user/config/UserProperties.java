package com.pluchon.meng.user.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// 用户服务的业务配置，启动时整体校验，缺项直接启动失败
@Validated
@ConfigurationProperties("meng.user")
public record UserProperties(
    @Valid @NotNull Session session,
    @Valid @NotNull Verification verification,
    @Valid @NotNull Limits limits,
    @NotEmpty Map<String, String> phonePatterns,
    @Valid @NotNull Notification notification,
    @Valid @NotNull Cookie cookie,
    @Valid @NotNull Sms sms,
    @Valid @NotNull InternationalSms internationalSms,
    @Valid @NotNull Legal legal
) {
    public record Legal(@NotBlank String termsVersion, @NotBlank String privacyVersion, boolean published) {}

    public record InternationalSms(
        boolean enabled,
        @NotBlank String endpoint,
        String senderId,
        @NotBlank String codeText,
        @NotBlank String approvedText,
        @NotBlank String rejectedText,
        boolean approvalNotificationEnabled
    ) {
        @AssertTrue(message = "Enabled international SMS requires a sender ID")
        public boolean isConfigured() {
            return !enabled || (senderId != null && !senderId.isBlank());
        }
    }

    public record Session(@NotNull Duration ttl) {}

    public record Verification(
        @NotNull Duration ticketTtl,
        @NotNull Duration codeTtl,
        @Min(1) int maxErrors,
        @Min(4) @Max(8) int codeDigits
    ) {}

    public record Limits(
        @NotNull Duration sendInterval,
        @NotNull Duration sendWindow,
        @Min(1) int sendWindowMax,
        @Min(1) int smsIpDailyMax,
        @Min(1) int registrationIpDailyMax,
        @Min(1) int reapplicationDailyMax,
        @Min(1) int loginMaxFailures,
        @NotNull Duration loginLockDuration
    ) {}

    public record Notification(
        @NotNull Duration rpcTimeout,
        @NotBlank String codeSubject,
        @NotBlank String codeText,
        @NotBlank String approvedSubject,
        @NotBlank String approvedText,
        @NotBlank String rejectedSubject,
        @NotBlank String rejectedText,
        @NotBlank String sender
    ) {}

    public record Cookie(boolean secure) {}

    // 短信配置必须显式给出；关闭时发送请求直接失败，不假装发出去了
    public record Sms(
        boolean enabled,
        @NotBlank String endpoint,
        String signName,
        String domesticCodeTemplate,
        boolean approvalNotificationEnabled,
        String approvedTemplate,
        String rejectedTemplate
    ) {
        @AssertTrue(message = "Enabled SMS requires a sign name and domestic code template")
        public boolean isConfigured() {
            return (
                !enabled ||
                (signName != null &&
                    !signName.isBlank() &&
                    domesticCodeTemplate != null &&
                    !domesticCodeTemplate.isBlank())
            );
        }
    }
}
