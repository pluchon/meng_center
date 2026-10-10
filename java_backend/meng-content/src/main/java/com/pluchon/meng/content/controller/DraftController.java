package com.pluchon.meng.content.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.content.controller.dto.SaveDraftDTO;
import com.pluchon.meng.content.controller.vo.*;
import com.pluchon.meng.content.service.DraftService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/content/drafts")
@RequiredArgsConstructor
public class DraftController {

    private final DraftService service;

    @GetMapping
    public ApiResponse<List<DraftSummaryVO>> list() {
        return ApiResponse.success(service.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<DraftVO> get(@PathVariable @Positive long id) {
        return ApiResponse.success(service.get(id));
    }

    @PostMapping
    public ApiResponse<Long> create(@RequestBody @Valid SaveDraftDTO body) {
        return ApiResponse.success(service.save(null, body));
    }

    @PutMapping("/{id}")
    public ApiResponse<Long> save(
        @PathVariable @Positive long id,
        @RequestBody @Valid SaveDraftDTO body
    ) {
        return ApiResponse.success(service.save(id, body));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}
