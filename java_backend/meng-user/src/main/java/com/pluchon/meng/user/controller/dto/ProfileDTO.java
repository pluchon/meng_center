package com.pluchon.meng.user.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 这里只按 UTF-16 粗限总长；昵称 16 字、签名 50 字按看得见的字数在服务里校验，emoji 不会被算成两个
public record ProfileDTO(
    @NotNull @Size(max = 64) String nickname,
    @NotNull @Size(max = 200) String bio,
    @NotNull @Size(max = 32) String username
) {}
