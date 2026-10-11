package com.pluchon.meng.content.job;

import com.pluchon.meng.content.service.HotRankService;
import com.pluchon.meng.content.service.PostSearchIndex;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// 内容域定时维护任务入口
@Component
public class ContentRebuildJobs {

    // 热榜维护
    @Autowired
    private HotRankService hot;

    // 搜索索引维护
    @Autowired
    private PostSearchIndex search;

    @XxlJob("contentHotRebuild")
    public void hot() {
        if (!hot.rebuild()) XxlJobHelper.handleFail("Hot rank rebuild failed");
    }

    @XxlJob("contentSearchRebuild")
    public void search() {
        if (!search.rebuild()) XxlJobHelper.handleFail("Search index rebuild failed");
    }
}
