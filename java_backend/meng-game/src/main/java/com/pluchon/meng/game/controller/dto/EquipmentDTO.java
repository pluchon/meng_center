package com.pluchon.meng.game.controller.dto;

import jakarta.validation.constraints.Size;

// 换装参数，传空表示不戴
public record EquipmentDTO(
        // 帽子标识
        @Size(max = 40) String hat,
        // 衣服颜色标识
        @Size(max = 40) String outfit) {}
