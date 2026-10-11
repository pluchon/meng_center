package com.pluchon.meng.content.validation;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;

import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

// 评论正文、表情和配图合计校验
public final class CommentRules {

    private CommentRules() {}

    // 允许的表情记号匹配规则
    private static final Pattern EMOTION = Pattern.compile("\\[表情:([^\\s\\[\\]]{1,12})\\]");

    public static void validate(String text, List<Long> images) {
        if ((text.isBlank() && images.isEmpty())
                || text.codePointCount(0, text.length()) > 500
                || images.size() > 10
                || new HashSet<>(images).size() != images.size()) invalid();
        var matcher = EMOTION.matcher(text);
        int count = 0;
        var stripped = matcher.replaceAll("");
        if (stripped.contains("[表情:")) invalid();
        matcher.reset();
        while (matcher.find()) if (++count + images.size() > 10) invalid();
    }

    private static void invalid() {
        throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
    }
}
