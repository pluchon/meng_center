package com.pluchon.meng.api.content.contract;

import com.pluchon.meng.common.core.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

// 萌萌只读工具契约，响应不含联系方式或属地
public interface CompanionPostContract {
    record Post(long id, String title, String summary, String authorNickname) {}

    @GetMapping("/content/internal/posts/search")
    ApiResponse<List<Post>> search(@RequestParam String q, @RequestParam(defaultValue="5") int size);

    @GetMapping("/content/internal/users/{id}/recent-posts")
    ApiResponse<List<Post>> recent(@PathVariable long id, @RequestParam(defaultValue="5") int size);
}
