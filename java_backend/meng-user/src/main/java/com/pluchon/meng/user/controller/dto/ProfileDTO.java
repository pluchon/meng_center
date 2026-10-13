package com.pluchon.meng.user.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProfileDTO(
    @NotNull @Size(max = 16) String nickname,
    @NotNull @Size(max = 50) String bio,
    @NotNull @Size(max = 32) String username
) {}
