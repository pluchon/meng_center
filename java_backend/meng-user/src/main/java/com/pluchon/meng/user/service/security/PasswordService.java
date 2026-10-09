package com.pluchon.meng.user.service.security;

import cn.hutool.crypto.digest.BCrypt;
import com.pluchon.meng.common.core.error.*;
import com.pluchon.meng.user.service.error.UserErrorCode;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

@Service
public final class PasswordService {

    private static final int MIN_LENGTH = 8;
    private static final int BCRYPT_MAX_BYTES = 72;
    private final String dummyHash = BCrypt.hashpw("unusable-account-placeholder", BCrypt.gensalt());

    public String hash(String password, String confirmation) {
        if (
            password == null ||
            password.length() < MIN_LENGTH ||
            password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES
        ) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        if (!password.equals(confirmation)) throw new BusinessException(UserErrorCode.PASSWORD_MISMATCH);
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    public boolean matches(String password, String storedHash) {
        boolean matched = false;
        try {
            matched = BCrypt.checkpw(password == null ? "" : password, storedHash == null ? dummyHash : storedHash);
        } catch (IllegalArgumentException ignored) {
            /* Invalid input is never authenticated. */
        }
        return storedHash != null && matched;
    }
}
