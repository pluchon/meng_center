package com.pluchon.meng.content.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.content.controller.dto.SaveDraftDTO;
import com.pluchon.meng.content.controller.vo.DraftSummaryVO;
import com.pluchon.meng.content.controller.vo.DraftVO;
import com.pluchon.meng.content.service.DraftService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 帖子草稿接口
@RestController
@RequestMapping("/content/drafts")
public class DraftController {

    // 对应接口的业务操作
    @Autowired
    private DraftService service;

    /** 查询当前用户草稿列表。 */
    @GetMapping
    public ApiResponse<List<DraftSummaryVO>> list() {
        return ApiResponse.success(service.list());
    }

    /** 读取指定草稿的编辑数据。 */
    @GetMapping("/{id}")
    public ApiResponse<DraftVO> get(@PathVariable @Positive long id) {
        return ApiResponse.success(service.get(id));
    }

    /** 创建草稿。 */
    @PostMapping
    public ApiResponse<Long> create(@RequestBody @Valid SaveDraftDTO body) {
        return ApiResponse.success(service.save(null, body));
    }

    /** 更新草稿内容。 */
    @PutMapping("/{id}")
    public ApiResponse<Long> save(
            @PathVariable @Positive long id, @RequestBody @Valid SaveDraftDTO body) {
        return ApiResponse.success(service.save(id, body));
    }

    /** 删除草稿。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}
