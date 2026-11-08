package com.pluchon.meng.common.core.text;

import java.text.Normalizer;
import java.util.regex.Pattern;

// 去掉看不见却会影响显示或被模型读到的字符：控制符、零宽字符、文字方向控制和 Unicode 标签区。
// 零宽连接符和变体选择符是 emoji 的组成部分，保留；换行和制表符留给各业务自己决定。
public final class InvisibleText {

    private static final Pattern INVISIBLE = Pattern.compile(
        "[\\p{Cc}&&[^\\n\\t]]|[\\u00AD\\u200B\\u200C\\u2060-\\u2064\\uFEFF\\u202A-\\u202E\\u2066-\\u2069\\uFFF9-\\uFFFB]|[\\x{E0000}-\\x{E007F}]"
    );

    private InvisibleText() {}

    // 规范成 NFC 后去掉不可见字符并去掉首尾空白；null 视为空串
    public static String clean(String text) {
        if (text == null) return "";
        return INVISIBLE.matcher(Normalizer.normalize(text, Normalizer.Form.NFC)).replaceAll("").strip();
    }
}
