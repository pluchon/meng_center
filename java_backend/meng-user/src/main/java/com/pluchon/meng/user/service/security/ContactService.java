package com.pluchon.meng.user.service.security;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.user.config.UserProperties;
import com.pluchon.meng.user.entity.enums.ContactChannel;
import com.pluchon.meng.user.service.error.UserErrorCode;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// 联系方式的归一化、加密和脱敏；邮箱与手机号二选一
@Service
public final class ContactService {

    private static final int EMAIL_MAX_LENGTH = 254;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    // 手机号允许带空格、括号和连字符，归一化时去掉
    private static final Pattern PHONE_INPUT = Pattern.compile("[0-9 ()-]+");
    private static final Pattern PHONE_SEPARATORS = Pattern.compile("[ ()-]");
    private static final Pattern LEADING_ZEROS = Pattern.compile("^0+");
    private static final String MASK = "***";

    // 各地区号码格式来自配置，编译一次后复用
    private final Map<String, Pattern> formats = new ConcurrentHashMap<>();

    @Autowired
    private UserProperties properties;

    @Autowired
    private PiiCipher cipher;

    // 邮箱去空白、转小写后校验格式
    public String normalizeEmail(String input) {
        if (input == null) {
            throw new BusinessException(UserErrorCode.CONTACT_INVALID);
        }
        String value = input.strip().toLowerCase(Locale.ROOT);
        if (value.length() > EMAIL_MAX_LENGTH || !EMAIL.matcher(value).matches()) {
            throw new BusinessException(UserErrorCode.CONTACT_INVALID);
        }
        return value;
    }

    // 手机号去分隔符和本地前导零，按所选地区校验后拼成「区号 + 号码」
    public String normalizePhone(String countryCode, String input) {
        String format = countryCode == null ? null : properties.phonePatterns().get(countryCode);
        if (format == null || input == null || !PHONE_INPUT.matcher(input).matches()) {
            throw new BusinessException(UserErrorCode.CONTACT_INVALID);
        }
        String digits = PHONE_SEPARATORS.matcher(input).replaceAll("");
        String local = LEADING_ZEROS.matcher(digits).replaceFirst("");
        if (!formats.computeIfAbsent(format, Pattern::compile).matcher(local).matches()) {
            throw new BusinessException(UserErrorCode.CONTACT_INVALID);
        }
        return countryCode + local;
    }

    // 归一化后同时给出检索哈希和密文，明文不出这个方法
    public Contact normalize(String email, String countryCode, String phone) {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasPhone = phone != null && !phone.isBlank();
        if (hasEmail == hasPhone) {
            throw new BusinessException(UserErrorCode.CONTACT_REQUIRED);
        }
        String normalized = hasEmail ? normalizeEmail(email) : normalizePhone(countryCode, phone);
        return new Contact(
            hasEmail ? ContactChannel.EMAIL : ContactChannel.SMS,
            cipher.hash(normalized),
            cipher.encrypt(normalized)
        );
    }

    // 邮箱只露首字符和域名
    public String maskEmail(String encrypted) {
        if (encrypted == null) {
            return null;
        }
        String plain = cipher.decrypt(encrypted);
        return plain.substring(0, 1) + MASK + plain.substring(plain.indexOf('@'));
    }

    // 手机号只露区号和末三位
    public String maskPhone(String encrypted) {
        if (encrypted == null) {
            return null;
        }
        String plain = cipher.decrypt(encrypted);
        return countryCode(plain) + MASK + plain.substring(plain.length() - 3);
    }

    // 取最长匹配的区号，避免 +886 被当成 +86
    public String countryCode(String normalizedPhone) {
        return properties
            .phonePatterns()
            .keySet()
            .stream()
            .filter(normalizedPhone::startsWith)
            .max(Comparator.comparingInt(String::length))
            .orElseThrow();
    }

    public record Contact(ContactChannel channel, String hash, String cipher) {}
}
