package com.pluchon.meng.user.job;

import com.pluchon.meng.user.service.ProfileModerationService;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// 资料审核发件箱的定时补发入口
@Component
public class UserAiJobs {

    // 兴趣画像或资料审核业务
    @Autowired
    private ProfileModerationService profiles;

    @XxlJob("userAiOutboxDispatch")
    public void dispatch() {
        profiles.dispatch();
    }
}
