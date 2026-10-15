package com.pluchon.meng.game.controller.dto;

import jakarta.validation.constraints.NotNull;

// 位置存档参数，朝向是弧度
public record SaveDTO(@NotNull Double x, @NotNull Double y, @NotNull Double z, @NotNull Double yaw) {}
