package com.pluchon.meng.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.web.context.RequestIdentity;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.user.controller.dto.ContactChangeDTO;
import com.pluchon.meng.user.controller.dto.ProfileDTO;
import com.pluchon.meng.user.entity.UserEntity;
import com.pluchon.meng.user.entity.enums.ContactChannel;
import com.pluchon.meng.user.entity.enums.VerificationPurpose;
import com.pluchon.meng.user.mapper.UserMapper;
import com.pluchon.meng.user.service.ContactChangeNotification;
import com.pluchon.meng.user.service.ProfileService;
import com.pluchon.meng.user.service.error.UserErrorCode;
import com.pluchon.meng.user.service.security.AuthorizationService;
import com.pluchon.meng.user.service.security.ContactService;
import com.pluchon.meng.user.service.security.PasswordService;
import com.pluchon.meng.user.service.security.RateLimitService;
import com.pluchon.meng.user.service.security.VerificationCodeService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProfileServiceTest {

    private final UserMapper users = mock(UserMapper.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final PasswordService passwords = mock(PasswordService.class);
    private final RateLimitService limits = mock(RateLimitService.class);
    private final ContactService contacts = mock(ContactService.class);
    private final VerificationCodeService codes = mock(VerificationCodeService.class);
    private final ContactChangeNotification notifications = mock(ContactChangeNotification.class);
    private final ProfileService service = new ProfileService();

    {
        ReflectionTestUtils.setField(
            service,
            "moderation",
            mock(com.pluchon.meng.user.service.ProfileModerationService.class)
        );
    }

    private final UserEntity user = new UserEntity();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-13T04:00:00Z"), ZoneOffset.UTC);
    private final ContactChangeDTO change = new ContactChangeDTO(
        "new@example.invalid",
        null,
        null,
        null,
        "123456",
        "correct"
    );

    @BeforeEach
    void setup() {
        UserContext.bind(new RequestIdentity(7L, "USER", "session"));
        user.setId(7L);
        user.setUsername("member");
        user.setNickname("原昵称");
        user.setBio("原签名");
        user.setPasswordHash("hash");
        user.setEmailCipher("old-cipher");
        user.setEmailHash("old-hash");
        user.setPhoneCipher("phone-cipher");
        user.setPhoneHash("phone-hash");
        when(users.lockById(7L)).thenReturn(user);
        when(authorization.requireActive(user)).thenReturn(user);
        when(passwords.matches("correct", "hash")).thenReturn(true);
        Map.of(
            "users",
            users,
            "authorization",
            authorization,
            "passwords",
            passwords,
            "limits",
            limits,
            "contacts",
            contacts,
            "codes",
            codes,
            "clock",
            clock,
            "notifications",
            notifications
        ).forEach((name, value) -> ReflectionTestUtils.setField(service, name, value));
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void cooldownRejectsWholeProfileWithoutSavingNicknameOrBio() {
        user.setUsernameUpdateTime(LocalDateTime.of(2026, 10, 12, 12, 0));
        var error = assertThrows(BusinessException.class, () ->
            service.update(new ProfileDTO("新昵称", "新签名", "new_member"))
        );
        assertEquals(UserErrorCode.USERNAME_COOLDOWN, error.errorCode());
        assertEquals("原昵称", user.getNickname());
        assertEquals("原签名", user.getBio());
        verify(users, never()).updateById(any(UserEntity.class));
    }

    @Test
    void cooldownStillAllowsNicknameChangeWhenUsernameIsUnchanged() {
        user.setUsernameUpdateTime(LocalDateTime.of(2026, 10, 12, 12, 0));
        service.update(new ProfileDTO(" 新昵称 ", "新签名", "MEMBER"));
        assertEquals("新昵称", user.getNickname());
        assertEquals("member", user.getUsername());
        verify(users).updateById(user);
    }

    @Test
    void trailingLineBreakCannotBypassSingleLineBioPolicy() {
        assertThrows(BusinessException.class, () ->
            service.update(new ProfileDTO("新昵称", "签名\n", "member"))
        );
        verify(users, never()).updateById(any(UserEntity.class));
    }

    @Test
    void wrongCurrentPasswordCountsAsLoginFailureAndDoesNotConsumeCode() {
        var error = assertThrows(BusinessException.class, () ->
            service.contact(
                new ContactChangeDTO(change.email(), null, null, null, "123456", "wrong"),
                true
            )
        );
        assertEquals(UserErrorCode.PASSWORD_WRONG, error.errorCode());
        verify(limits).failedLogin("member");
        verifyNoInteractions(codes, contacts, notifications);
        verify(users, never()).updateById(any(UserEntity.class));
    }

    @Test
    void contactChangeUsesDedicatedCodeAndPreservesOtherContact() {
        var contact = new ContactService.Contact(ContactChannel.EMAIL, "new-hash", "new-cipher");
        when(contacts.normalize(change.email(), null, null)).thenReturn(contact);
        service.contact(change, true);
        verify(codes).consume(contact, "123456", VerificationPurpose.CONTACT_CHANGE);
        assertEquals("phone-cipher", user.getPhoneCipher());
        assertEquals("phone-hash", user.getPhoneHash());
        verify(notifications).changed("old-cipher");
        verify(users).updateById(user);
    }

    @Test
    void reencryptingSameEmailDoesNotSendChangeNotification() {
        var contact = new ContactService.Contact(
            ContactChannel.EMAIL,
            "old-hash",
            "new-random-cipher"
        );
        when(contacts.normalize(change.email(), null, null)).thenReturn(contact);
        service.contact(change, true);
        verifyNoInteractions(notifications);
    }
}
