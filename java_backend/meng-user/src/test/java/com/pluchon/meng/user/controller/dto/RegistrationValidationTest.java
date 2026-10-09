package com.pluchon.meng.user.controller.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class RegistrationValidationTest {
    @Test
    void registrationAndReapplicationRejectReasonsOverOneHundredCharacters() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (int length : new int[] {100, 101}) {
                String reason = "字".repeat(length);
                int expected = length == 100 ? 0 : 1;
                var registration = new RegistrationDTO(
                    "test_user", "password123", "password123", null, "+886", "988776654",
                    "123456", "verification-ticket", reason, "2026-10-09", "2026-10-09"
                );
                var reapplication = new ReapplicationDTO("test_user", "password123", "verification-ticket", reason);
                assertEquals(expected, validator.validate(registration).size());
                assertEquals(expected, validator.validate(reapplication).size());
            }
        }
    }
    @Test
    void registrationPasswordRequiresEightToTwentyLettersAndDigits() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            String[] valid = {"abcd1234", "a1".repeat(10)};
            String[] invalid = {"abc1234", "a1".repeat(10) + "a", "abcdefgh", "12345678", "abc1234!", "密码abc123"};
            for (String password : valid) assertEquals(0, validator.validateValue(RegistrationDTO.class, "password", password).size());
            for (String password : invalid) assertEquals(1, validator.validateValue(RegistrationDTO.class, "password", password).size());
        }
    }

}
