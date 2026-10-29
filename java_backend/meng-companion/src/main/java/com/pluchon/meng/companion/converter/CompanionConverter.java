package com.pluchon.meng.companion.converter;

import com.pluchon.meng.companion.controller.CompanionVO;
import com.pluchon.meng.companion.entity.Rows;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

// 显式构造对外字段，避免持久化字段进入响应。
public final class CompanionConverter {
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private CompanionConverter() {}
    public static CompanionVO.Session session(Rows.Session row) {
        return new CompanionVO.Session(row.getId(), row.getTitle(), row.getLastMessageTime(), "",
                                       row.isUnread(), row.isGenerating());
    }
    public static CompanionVO.Message message(Rows.Message row, boolean ignored) {
        var images = new ArrayList<Long>();
        if (row.getImageIds() != null) JSON.readTree(row.getImageIds()).forEach(id -> images.add(id.asLong()));
        var sources = row.getSources() == null ? List.<CompanionVO.Source>of()
            : CompanionSources.read(JSON.readTree(row.getSources()));
        return new CompanionVO.Message(row.getId(), row.getRole(), row.getKind(), row.getContent(), row.getSticker(),
            List.copyOf(images), List.copyOf(sources), row.getCreateTime(), row.getClientMessageId(), row.getTurnState());
    }
}
