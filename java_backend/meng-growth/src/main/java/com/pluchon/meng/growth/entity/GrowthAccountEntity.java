package com.pluchon.meng.growth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

// 成长账户持久化实体
@Data
@TableName("growth_account")
public class GrowthAccountEntity {

    // 记录主键
    @TableId(type = IdType.AUTO)
    private Long id;

    // 用户编号
    private Long userId;

    // 累计经验
    private Long exp;

    // 当前等级
    private Integer level;
}
