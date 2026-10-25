package com.pluchon.meng.content.controller;

import com.pluchon.meng.api.content.contract.EmotionContract;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.content.service.EmotionCatalog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmotionController implements EmotionContract {
    @Autowired private EmotionCatalog catalog;
    @Override public ApiResponse<Catalog> internalEmotions() { return ApiResponse.success(catalog.read()); }
    @GetMapping("/content/emotions")
    public ApiResponse<Catalog> emotions() { return ApiResponse.success(catalog.read()); }
}
