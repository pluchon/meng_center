package com.pluchon.meng.companion.job;

import com.pluchon.meng.companion.service.*;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// 调度入口只转发本域服务方法，恢复任务同时处理遗留的排队轮次。
@Component
public class CompanionJobs {

    @Autowired
    private CompanionOutbox outbox;

    @Autowired
    private CompanionBackground background;

    @Autowired
    private ReplyService replies;

    @XxlJob("companionOutboxDispatch")
    public void dispatch() {
        outbox.dispatch();
        replies.recover();
    }

    @XxlJob("companionMemoryMaintain")
    public void maintain() {
        background.maintain();
    }

}
