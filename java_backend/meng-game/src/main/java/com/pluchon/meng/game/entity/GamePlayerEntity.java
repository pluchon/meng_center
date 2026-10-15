package com.pluchon.meng.game.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.time.LocalDateTime;

// 玩家档案持久化实体
@Data
@TableName("game_player")
public class GamePlayerEntity {

    // 记录主键
    @TableId(type = IdType.AUTO)
    private Long id;

    // 用户编号
    private Long userId;

    // 金币余额
    private Long coins;

    // 上次存档的位置
    private Double posX;

    private Double posY;

    private Double posZ;

    // 上次存档的朝向，弧度
    private Double yaw;

    // 已装备的帽子
    private String hat;

    // 已装备的衣服颜色
    private String outfit;

    // 点亮神殿的时间，空表示还没点亮
    private LocalDateTime clearedTime;
}
