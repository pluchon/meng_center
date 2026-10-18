package com.pluchon.meng.companion.converter;

import com.pluchon.meng.companion.controller.CompanionVO;
import com.pluchon.meng.companion.entity.Rows;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

// 显式转换持久化记录，防止隐私字段进入响应
public final class CompanionConverter {

    private CompanionConverter() {}

    private static final JsonMapper JSON = JsonMapper.builder()
        .findAndAddModules()
        .build();

    public static CompanionVO.Session session(Rows.Session row) {
        return new CompanionVO.Session(
            row.getId(),
            row.getCharacterCode(),
            row.getTitle(),
            row.getLastMessageTime(),
            null,
            row.isUnread(),
            row.isGenerating(),
            row.isDrawing()
        );
    }

    public static CompanionVO.Message message(Rows.Message row) {
        var ids = new ArrayList<Long>();
        if (row.getImageIds() != null) for (var item : JSON.readTree(
            row.getImageIds()
        ))
            ids.add(item.asLong());
        return new CompanionVO.Message(
            row.getId(),
            row.getRole(),
            row.getContent(),
            List.copyOf(ids),
            row.getWorkId(),
            row.getCreateTime(),
            row.getKind(),
            row.getSticker(),
            row.getClientMessageId(),
            row.getTurnState(),
            "CHARACTER".equals(row.getRole()) &&
                row.getTurnId() != null &&
                row.getRegeneratesMessageId() == null &&
                row.getWorkId() == null
        );
    }

    public static CompanionVO.Work work(Rows.Work row, boolean detail) {
        return new CompanionVO.Work(
            row.getId(),
            row.getCharacterCode(),
            row.getSessionId(),
            row.getKind(),
            row.getTitle(),
            detail ? row.getBody() : null,
            "/companion/works/" + row.getId() + "/file",
            row.getCreateTime(),
            row.getParentId()
        );
    }
}
