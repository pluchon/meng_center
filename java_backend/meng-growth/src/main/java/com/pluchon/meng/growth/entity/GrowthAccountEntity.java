package com.pluchon.meng.growth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("growth_account")
public class GrowthAccountEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long exp;
    private Integer level;
}
