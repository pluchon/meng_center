package com.pluchon.meng.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.user.config.UserProperties;
import com.pluchon.meng.user.controller.dto.PasswordResetDTO;
import com.pluchon.meng.user.entity.UserEntity;
import com.pluchon.meng.user.entity.enums.*;
import com.pluchon.meng.user.mapper.UserMapper;
import com.pluchon.meng.user.service.PasswordResetService;
import com.pluchon.meng.user.service.CaptchaService;
import com.pluchon.meng.user.service.error.UserErrorCode;
import com.pluchon.meng.user.service.security.*;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PasswordResetServiceTest {

    private final ContactService contacts = mock(ContactService.class);
    private final VerificationCodeService codes = mock(
        VerificationCodeService.class
    );
    private final CaptchaService captcha = mock(CaptchaService.class);
    private final RateLimitService limits = mock(RateLimitService.class);
    private final UserProperties properties = mock(UserProperties.class);
    private final UserMapper mapper = mock(UserMapper.class);
    private final PasswordService passwords = mock(PasswordService.class);
    private final SessionService sessions = mock(SessionService.class);
    private final ContactService.Contact contact = new ContactService.Contact(
        ContactChannel.EMAIL,
        "hash",
        "encrypted"
    );
    private final PasswordResetDTO request = new PasswordResetDTO(
        "test@example.invalid",
        null,
        null,
        "123456",
        "new12345",
        "new12345",
        "ticket"
    );
    private PasswordResetService service;

    @BeforeEach
    void setup() {
        when(contacts.normalize(request.email(), null, null)).thenReturn(
            contact
        );
        when(properties.verification()).thenReturn(
            new UserProperties.Verification(
                Duration.ofMinutes(2),
                Duration.ofMinutes(5),
                5,
                6
            )
        );
        when(
            passwords.hash(request.password(), request.confirmPassword())
        ).thenReturn("bcrypt");
        service = new PasswordResetService(
            contacts,
            codes,
            captcha,
            limits,
            properties,
            mapper,
            passwords,
            sessions
        );
    }

    private UserEntity user(UserRole role) {
        var user = new UserEntity();
        user.setId(7L);
        user.setUsername("test");
        user.setRole(role);
        user.setStatus(UserStatus.PENDING);
        user.setPasswordHash("old");
        return user;
    }

    @Test
    void validResetRevokesEverySessionAndPreservesReviewState() {
        var user = user(UserRole.USER);
        when(mapper.lockByEmailHash(contact.hash())).thenReturn(user);
        service.reset(request, "ip");
        assertEquals("bcrypt", user.getPasswordHash());
        assertEquals(UserStatus.PENDING, user.getStatus());
        var order = inOrder(captcha, codes, mapper, sessions);
        order.verify(captcha).consume("ticket", "ip");
        order
            .verify(codes)
            .consume(contact, "123456", VerificationPurpose.PASSWORD_RESET);
        order.verify(mapper).lockByEmailHash("hash");
        order.verify(mapper).updateById(user);
        order.verify(sessions).revokeAll(7L);
        verify(limits).clearLogin("test");
    }

    @Test
    void invalidResetCodeCannotReachAccountOrChangePassword() {
        doThrow(new BusinessException(UserErrorCode.INVALID_CODE))
            .when(codes)
            .consume(contact, "123456", VerificationPurpose.PASSWORD_RESET);
        assertThrows(BusinessException.class, () ->
            service.reset(request, "ip")
        );
        verifyNoInteractions(mapper, sessions);
    }

    @Test
    void missingAccountAndAdministratorCannotBeResetThroughPublicEndpoint() {
        assertThrows(BusinessException.class, () ->
            service.reset(request, "ip")
        );
        when(mapper.lockByEmailHash("hash")).thenReturn(user(UserRole.ADMIN));
        assertThrows(BusinessException.class, () ->
            service.reset(request, "ip")
        );
        verify(mapper, never()).updateById(any(UserEntity.class));
        verifyNoInteractions(sessions);
    }

    @Test
    void invalidCaptchaStopsCodeConsumptionAndDatabaseAccess() {
        doThrow(new BusinessException(UserErrorCode.INVALID_TICKET))
            .when(captcha)
            .consume("ticket", "ip");
        assertThrows(BusinessException.class, () ->
            service.reset(request, "ip")
        );
        verifyNoInteractions(codes, mapper, sessions);
    }

    @Test
    void phoneResetUsesPhoneHashLookupAndResetPurpose() {
        var phoneRequest = new PasswordResetDTO(
            null,
            "+886",
            "912345678",
            "123456",
            "new12345",
            "new12345",
            "ticket"
        );
        var phoneContact = new ContactService.Contact(
            ContactChannel.SMS,
            "phone-hash",
            "encrypted"
        );
        when(contacts.normalize(null, "+886", "912345678")).thenReturn(
            phoneContact
        );
        when(mapper.lockByPhoneHash("phone-hash")).thenReturn(
            user(UserRole.USER)
        );
        service.reset(phoneRequest, "ip");
        verify(codes).consume(
            phoneContact,
            "123456",
            VerificationPurpose.PASSWORD_RESET
        );
        verify(mapper, never()).lockByEmailHash(anyString());
        verify(sessions).revokeAll(7L);
    }
}
