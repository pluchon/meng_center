package com.pluchon.meng.user.service.security;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.user.config.UserProperties;
import com.pluchon.meng.user.entity.enums.ContactChannel;
import com.pluchon.meng.user.service.error.UserErrorCode;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public final class ContactService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final UserProperties properties;
    private final PiiCipher cipher;

    public ContactService(UserProperties properties, PiiCipher cipher) {
        this.properties = properties;
        this.cipher = cipher;
    }

    public String normalizeEmail(String input) {
        if (input == null) throw new BusinessException(UserErrorCode.CONTACT_INVALID);
        String value = input.strip().toLowerCase(Locale.ROOT);
        if (value.length() > 254 || !EMAIL.matcher(value).matches()) throw new BusinessException(
            UserErrorCode.CONTACT_INVALID
        );
        return value;
    }

    public String normalizePhone(String countryCode, String input) {
        String format = properties.phonePatterns().get(countryCode);
        if (format == null || input == null || !input.matches("[0-9 ()-]+")) throw new BusinessException(
            UserErrorCode.CONTACT_INVALID
        );
        String local = input.replaceAll("[ ()-]", "").replaceFirst("^0+", "");
        if (!local.matches(format)) throw new BusinessException(UserErrorCode.CONTACT_INVALID);
        return countryCode + local;
    }

    public Contact normalize(String email, String countryCode, String phone) {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasPhone = phone != null && !phone.isBlank();
        if (hasEmail == hasPhone) throw new BusinessException(UserErrorCode.CONTACT_REQUIRED);
        String normalized = hasEmail ? normalizeEmail(email) : normalizePhone(countryCode, phone);
        return new Contact(
            hasEmail ? ContactChannel.EMAIL : ContactChannel.SMS,
            cipher.hash(normalized),
            cipher.encrypt(normalized)
        );
    }

    public String maskEmail(String encrypted) {
        if (encrypted == null) return null;
        String plain = cipher.decrypt(encrypted);
        return plain.substring(0, 1) + "***" + plain.substring(plain.indexOf('@'));
    }

    public String maskPhone(String encrypted) {
        if (encrypted == null) return null;
        String plain = cipher.decrypt(encrypted);
        String prefix = countryCode(plain);
        return prefix + "***" + plain.substring(plain.length() - 3);
    }

    public String countryCode(String normalizedPhone) {
        return properties
            .phonePatterns()
            .keySet()
            .stream()
            .filter(normalizedPhone::startsWith)
            .max(java.util.Comparator.comparingInt(String::length))
            .orElseThrow();
    }

    public record Contact(ContactChannel channel, String hash, String cipher) {}
}
