package com.pluchon.meng.user.service.security;

import cn.hutool.crypto.digest.BCrypt;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.user.service.error.UserErrorCode;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

// 密码的哈希与校验，统一用 BCrypt
@Service
public final class PasswordService {

    private static final int MIN_LENGTH = 8;
    // BCrypt 只取前 72 字节，超出的部分不参与计算，所以直接拒绝
    private static final int BCRYPT_MAX_BYTES = 72;

    // 账号不存在时也跑一遍校验，避免用响应时间探测账号是否存在
    private final String dummyHash = BCrypt.hashpw("unusable-account-placeholder", BCrypt.gensalt());

    // 校验长度与两次输入一致后生成哈希
    public String hash(String password, String confirmation) {
        if (
            password == null ||
            password.length() < MIN_LENGTH ||
            password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES
        ) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        }
        if (!password.equals(confirmation)) {
            throw new BusinessException(UserErrorCode.PASSWORD_MISMATCH);
        }
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    // 没有存储哈希或输入非法时一律不通过
    public boolean matches(String password, String storedHash) {
        boolean matched = false;
        try {
            matched = BCrypt.checkpw(password == null ? "" : password, storedHash == null ? dummyHash : storedHash);
        } catch (IllegalArgumentException ignored) {
            // 哈希格式不合法时按不匹配处理
        }
        return storedHash != null && matched;
    }
}
