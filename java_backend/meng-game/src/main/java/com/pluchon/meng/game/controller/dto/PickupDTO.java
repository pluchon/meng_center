package com.pluchon.meng.game.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 拾取参数：点位和玩家此刻的水平位置
public record PickupDTO(
        // 点位标识
        @NotBlank @Size(max = 40) String spotId,
        // 玩家位置
        @NotNull Double x,
        @NotNull Double z) {}
