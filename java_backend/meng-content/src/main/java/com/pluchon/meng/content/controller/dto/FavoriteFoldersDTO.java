package com.pluchon.meng.content.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

// 设定帖子所在收藏夹的请求
public record FavoriteFoldersDTO(
    // 帖子要放进的全部收藏夹编号，可以为空，个数上限由 Service 判断
    @NotNull List<@Positive Long> folderIds
) {}
