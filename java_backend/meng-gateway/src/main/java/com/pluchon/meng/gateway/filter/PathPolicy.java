package com.pluchon.meng.gateway.filter;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public final class PathPolicy {

    private static final Pattern INTERNAL = Pattern.compile("(^|/)internal(/|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADMIN = Pattern.compile("(^|/)admin(/|$)", Pattern.CASE_INSENSITIVE);

    private PathPolicy() {}

    public static String normalize(String path) {
        String current = path;
        for (int i = 0; i < 8; i++) {
            String next = URLDecoder.decode(current.replace("+", "%2B"), StandardCharsets.UTF_8);
            if (next.equals(current)) break;
            current = next;
        }
        if (
            current.contains("%") || current.contains("\\") || current.indexOf('\0') >= 0
        ) throw new IllegalArgumentException("Invalid path encoding");
        String normalized = current.replaceAll(";[^/]*", "").replaceAll("/+", "/");
        for (String segment : normalized.split("/")) {
            if (segment.equals(".") || segment.equals("..")) throw new IllegalArgumentException(
                "Invalid path traversal"
            );
        }
        return normalized;
    }

    public static boolean internal(String path) {
        return INTERNAL.matcher(path).find();
    }

    public static boolean admin(String path) {
        return ADMIN.matcher(path).find();
    }
}
