package com.pluchon.meng.content.validation;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

// 帖子、配图和标签参数校验
public final class PostRules {

    // 帖子标题码点上限
    public static final int TITLE_MAX = 50;

    // 帖子正文码点上限
    public static final int CONTENT_MAX = 10000;

    // 帖子配图数量上限
    public static final int IMAGES_MAX = 10;

    // 帖子标签数量上限
    public static final int TAGS_MAX = 5;

    // 单个标签码点上限
    public static final int TAG_MAX = 12;

    // 每个用户的草稿数量上限
    public static final int DRAFTS_MAX = 20;

    private PostRules() {}

    public static String tagKey(String value) {
        var out = new StringBuilder();
        value.codePoints()
                .forEach(
                        c ->
                                out.appendCodePoint(
                                        c == 0x3000
                                                ? ' '
                                                : c >= 0xff01 && c <= 0xff5e ? c - 0xfee0 : c));
        return out.toString().strip().toLowerCase(Locale.ROOT);
    }

    public static List<String> tags(List<String> input) {
        if (input.size() > TAGS_MAX) invalid();
        var result = new ArrayList<String>();
        var keys = new HashSet<String>();
        for (String name : input) {
            if (name == null) invalid();
            String key = tagKey(name);
            if (key.isEmpty()
                    || key.codePointCount(0, key.length()) > TAG_MAX
                    || key.codePoints()
                            .anyMatch(
                                    c ->
                                            Character.isWhitespace(c)
                                                    || Character.isSpaceChar(c)
                                                    || c == '#')) invalid();
            if (keys.add(key)) result.add(name.strip());
        }
        return result;
    }

    public static void images(List<Long> ids, Long cover) {
        if (ids.size() > IMAGES_MAX
                || ids.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(ids).size() != ids.size()
                || (cover != null && (cover <= 0 || ids.contains(cover)))) invalid();
    }

    private static void invalid() {
        throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
    }
}
