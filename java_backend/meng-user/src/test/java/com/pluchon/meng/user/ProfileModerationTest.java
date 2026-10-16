package com.pluchon.meng.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluchon.meng.user.service.ProfileModerationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class ProfileModerationTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ProfileModerationService service = new ProfileModerationService();
    private final LocalDateTime version = LocalDateTime.parse("2026-10-16T03:00:00.123456");

    private Map<String, Object> result() {
        return Map.of(
            "userId",
            9L,
            "version",
            version.toString(),
            "verdict",
            "PASS",
            "labels",
            List.of(),
            "reason",
            "正常文字",
            "modelVersion",
            "fake",
            "avatarChanged",
            false,
            "imageReviews",
            List.of()
        );
    }

    @Test
    void staleProfileResultNeverChangesCurrentProfile() {
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        when(jdbc.queryForList(contains("FOR UPDATE"), eq(9L))).thenReturn(
            List.of(Map.of("profile_version", version.plusSeconds(1)))
        );
        service.result(result());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void textEditInheritsSameAvatarFindingWithoutEnforcingIt() {
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        when(jdbc.queryForList(contains("FOR UPDATE"), eq(9L))).thenReturn(
            List.of(
                Map.of(
                    "profile_version",
                    version,
                    "nickname",
                    "云朵",
                    "bio",
                    "普通签名",
                    "avatar",
                    "avatar.webp"
                )
            )
        );
        when(
            jdbc.queryForList(contains("SELECT avatar_review"), eq(9L), eq("avatar.webp"))
        ).thenReturn(
            List.of(
                Map.of(
                    "avatar_review",
                    "{\"verdict\":\"BLOCK\",\"labels\":[\"暴力\"],\"reason\":\"头像待复核\"}"
                )
            )
        );
        var saved = new AtomicReference<Object[]>();
        when(jdbc.update(anyString(), any(Object[].class))).thenAnswer(call -> {
            if (call.<String>getArgument(0).startsWith("INSERT IGNORE")) saved.set(
                call.getArguments()
            );
            return 1;
        });
        service.result(result());
        assertNotNull(saved.get());
        assertEquals("BLOCK", saved.get()[3]);
        verify(jdbc, never()).update(startsWith("UPDATE user SET nickname"), any(Object[].class));
    }

    @Test
    void enforcementClearsOnlyTheBlockedPart() {
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "enforce", true);
        ReflectionTestUtils.setField(service, "clock", java.time.Clock.systemUTC());
        ReflectionTestUtils.setField(service, "events", mock(org.springframework.context.ApplicationEventPublisher.class));
        when(jdbc.queryForList(contains("FOR UPDATE"), eq(9L))).thenReturn(
            List.of(
                Map.of(
                    "profile_version",
                    version,
                    "nickname",
                    "云朵",
                    "bio",
                    "普通签名",
                    "avatar",
                    "avatar.webp"
                )
            )
        );
        when(
            jdbc.queryForList(contains("SELECT avatar_review"), eq(9L), eq("avatar.webp"))
        ).thenReturn(
            List.of(
                Map.of(
                    "avatar_review",
                    "{\"verdict\":\"BLOCK\",\"labels\":[\"暴力\"],\"reason\":\"头像不合适\"}"
                )
            )
        );
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        service.result(result());
        verify(jdbc).update(
            startsWith("UPDATE user SET nickname=IF"),
            eq(false),
            eq(false),
            eq(true),
            eq(9L)
        );
    }
}
