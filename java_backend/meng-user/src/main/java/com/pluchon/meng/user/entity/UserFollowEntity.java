package com.pluchon.meng.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

// 关注关系：谁关注了谁；取消关注只翻转删除标记，再次关注时翻回来
@Getter
@Setter
@TableName("user_follow")
public class UserFollowEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 关注的人
    private Long followerId;

    // 被关注的人
    private Long followeeId;

    // 逻辑删除标记，一为已取消关注
    private Integer deleteState;
}
