package com.pluchon.meng.companion.converter;

import com.pluchon.meng.companion.controller.CompanionVO;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

// 只接受公开来源字段，旧消息缺少类型时仍按站内帖子读取。
public final class CompanionSources {
    private CompanionSources() {}

    public static List<CompanionVO.Source> read(JsonNode nodes) {
        if (nodes.isMissingNode() || nodes.isNull()) return List.of();
        if (!nodes.isArray() || nodes.size() > 5) throw new IllegalArgumentException("来源数量不合法");
        var result = new ArrayList<CompanionVO.Source>();
        for (var row : nodes) {
            String type = row.path("type").asString("POST");
            String title = row.path("title").asString("");
            if (title.isBlank() || title.length() > 200) throw new IllegalArgumentException("来源标题不合法");
            if ("POST".equals(type)) {
                long post = row.path("postId").asLong();
                if (post <= 0) throw new IllegalArgumentException("帖子来源不合法");
                result.add(new CompanionVO.Source(type, post, title, null, null));
            } else if ("WEB".equals(type)) {
                String url = row.path("url").asString("");
                String site = row.path("site").asString("");
                URI address = URI.create(url);
                if (!"https".equals(address.getScheme()) || address.getHost() == null
                    || address.getRawUserInfo() != null || url.length() > 2000
                    || site.isBlank() || site.length() > 200)
                    throw new IllegalArgumentException("网页来源不合法");
                result.add(new CompanionVO.Source(type, null, title, url, site));
            } else throw new IllegalArgumentException("未知来源类型");
        }
        return List.copyOf(result);
    }
}
