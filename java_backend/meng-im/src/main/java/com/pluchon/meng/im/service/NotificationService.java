package com.pluchon.meng.im.service;

import com.pluchon.meng.im.entity.ImRows;
import com.pluchon.meng.im.mapper.ImMapper;
import com.pluchon.meng.im.realtime.ImSocket;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 数据库唯一键处理事件重复和反复互动
@Service
public class NotificationService {

    @Autowired
    private ImMapper mapper;

    @Autowired
    private ImService im;

    @Autowired
    private ImSocket socket;

    @Transactional(rollbackFor = Exception.class)
    public void receive(ImRows.Notice notice) {
        if (
            notice.getReceiverId() == null ||
            notice.getReceiverId() < 1 ||
            notice.getReceiverId().equals(notice.getActorId())
        ) return;
        if (mapper.insertNotice(notice) == 1) ImService.afterCommit(() -> {
            socket.push(
                notice.getReceiverId(),
                "notice.new",
                Map.of("channel", notice.getChannel())
            );
            im.pushUnread(notice.getReceiverId());
        });
    }
}
