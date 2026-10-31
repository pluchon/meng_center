package com.pluchon.meng.companion.service;

import java.util.regex.Pattern;

// 仅检查记忆长度和格式化联系方式，含义与隐私由独立审核判断。
public final class CompanionRules {

    private CompanionRules() {}

    // 格式化的联系方式和证件号（手机号、邮箱、15～18 位证件号），和 ai 的兜底规则一致；语义上的隐私由 AI 审核
    private static final Pattern CONTACT = Pattern.compile(
        "(?<!\\d)(?:\\+?\\d{1,3}[\\s-]?)?\\d{3,4}[\\s-]?\\d{3,4}[\\s-]?\\d{3,4}(?!\\d)" +
            "|[\\w.+-]+@[\\w-]+\\.[\\w.-]+|(?<!\\d)\\d{15,17}[\\dXx](?!\\d)"
    );

    // 看不见却会被模型读到的字符：控制符、零宽字符、文字方向控制和 Unicode 标签区。
    // 零宽连接符和变体选择符是 emoji 的组成部分，保留。
    private static final Pattern INVISIBLE = Pattern.compile(
        "[\\p{Cc}&&[^\\n\\t]]|[\\u00AD\\u200B\\u200C\\u2060-\\u2064\\uFEFF\\u202A-\\u202E\\u2066-\\u2069\\uFFF9-\\uFFFB]|[\\x{E0000}-\\x{E007F}]"
    );

    public static String cleanInput(String text) {
        return text == null ? "" : INVISIBLE.matcher(text).replaceAll("").strip();
    }

    public static boolean validMemory(String text) {
        return (
            text != null &&
            !text.isBlank() &&
            text.codePointCount(0, text.length()) <= 60 &&
            !CONTACT.matcher(text).find()
        );
    }

}
