package com.pluchon.meng.game.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 购买参数
public record PurchaseDTO(
        // 商品标识
        @NotBlank @Size(max = 40) String itemId) {}
