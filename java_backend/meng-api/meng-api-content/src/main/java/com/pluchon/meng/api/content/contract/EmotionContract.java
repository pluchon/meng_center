package com.pluchon.meng.api.content.contract;

import com.pluchon.meng.common.core.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;

// 共享表情目录只返回素材导出名，不包含私有存储路径。
public interface EmotionContract {
    record Emotion(String name,String series,String imageKey,int sort,boolean enabled,String description,String meaning,List<String> tags) {}
    record Catalog(String version,List<Emotion> items) {}
    @GetMapping("/content/internal/emotions")
    ApiResponse<Catalog> internalEmotions();
}
