package com.pluchon.meng.game.controller.vo;

// 玩家档案：金币、存档位置、外观和进度
public record ProfileVO(
        // 金币余额
        long coins,
        // 上次存档的位置和朝向
        double x,
        double y,
        double z,
        double yaw,
        // 已装备的帽子
        String hat,
        // 已装备的衣服颜色
        String outfit,
        // 已得到的星之碎片数
        int stars,
        // 是否已点亮神殿
        boolean cleared) {}
