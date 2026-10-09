package com.pluchon.meng.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pluchon.meng.user.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
    @Select("SELECT * FROM user WHERE username = #{username} AND delete_state = 0 FOR UPDATE")
    UserEntity lockByUsername(@Param("username") String username);

    @Select("SELECT * FROM user WHERE email_hash = #{hash} AND delete_state = 0 FOR UPDATE")
    UserEntity lockByEmailHash(@Param("hash") String hash);

    @Select("SELECT * FROM user WHERE phone_hash = #{hash} AND delete_state = 0 FOR UPDATE")
    UserEntity lockByPhoneHash(@Param("hash") String hash);

    @Select("SELECT * FROM user WHERE id = #{id} AND delete_state = 0 FOR UPDATE")
    UserEntity lockById(@Param("id") long id);
}
