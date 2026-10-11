package com.pluchon.meng.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pluchon.meng.user.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

// 账号表访问。lock 开头的方法加行锁，只能在事务里调用
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
    // 追加在查询末尾的行锁子句
    @org.apache.ibatis.annotations.Update(
        "UPDATE user SET level=#{level} WHERE id=#{id} AND level<#{level} AND delete_state=0"
    )
    int raiseLevel(
        @org.apache.ibatis.annotations.Param("id") long id,
        @org.apache.ibatis.annotations.Param("level") int level
    );

    String FOR_UPDATE = "FOR UPDATE";

    // 按用户名加锁读取
    default UserEntity lockByUsername(String username) {
        return selectOne(
            Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getUsername, username)
                .last(FOR_UPDATE)
        );
    }

    // 按邮箱检索哈希加锁读取
    default UserEntity lockByEmailHash(String hash) {
        return selectOne(
            Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getEmailHash, hash)
                .last(FOR_UPDATE)
        );
    }

    // 按手机号检索哈希加锁读取
    default UserEntity lockByPhoneHash(String hash) {
        return selectOne(
            Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getPhoneHash, hash)
                .last(FOR_UPDATE)
        );
    }

    // 按主键加锁读取
    default UserEntity lockById(long id) {
        return selectOne(
            Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getId, id)
                .last(FOR_UPDATE)
        );
    }
}
