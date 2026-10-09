package com.pluchon.meng.gateway.filter;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

// 路径的归一化与分类，鉴权判断只看归一化之后的路径
public final class PathPolicy {

    private static final Pattern INTERNAL = Pattern.compile("(^|/)internal(/|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADMIN = Pattern.compile("(^|/)admin(/|$)", Pattern.CASE_INSENSITIVE);
    // 分号后面的矩阵参数和连续斜杠都会让同一路径出现多种写法
    private static final Pattern MATRIX_PARAMETERS = Pattern.compile(";[^/]*");
    private static final Pattern REPEATED_SLASHES = Pattern.compile("/+");
    // 多重编码最多解这么多层，解不完的按非法路径处理
    private static final int MAX_DECODE_ROUNDS = 8;

    private PathPolicy() {}

    // 反复解码直到稳定，去掉矩阵参数和多余斜杠；残留编码、反斜杠、空字符和目录穿越一律拒绝
    public static String normalize(String path) {
        String current = path;
        for (int round = 0; round < MAX_DECODE_ROUNDS; round++) {
            String next = URLDecoder.decode(current.replace("+", "%2B"), StandardCharsets.UTF_8);
            if (next.equals(current)) {
                break;
            }
            current = next;
        }
        if (current.contains("%") || current.contains("\\") || current.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid path encoding");
        }
        String withoutParameters = MATRIX_PARAMETERS.matcher(current).replaceAll("");
        String normalized = REPEATED_SLASHES.matcher(withoutParameters).replaceAll("/");
        for (String segment : normalized.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Invalid path traversal");
            }
        }
        return normalized;
    }

    // 是否是服务之间的内部接口
    public static boolean internal(String path) {
        return INTERNAL.matcher(path).find();
    }

    // 是否是管理端接口
    public static boolean admin(String path) {
        return ADMIN.matcher(path).find();
    }
}
