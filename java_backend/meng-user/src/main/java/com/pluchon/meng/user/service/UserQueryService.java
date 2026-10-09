package com.pluchon.meng.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pluchon.meng.api.user.vo.PublicUserVO;
import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.user.controller.vo.UserVO;
import com.pluchon.meng.user.converter.UserConverter;
import com.pluchon.meng.user.entity.UserEntity;
import com.pluchon.meng.user.entity.enums.UserStatus;
import com.pluchon.meng.user.mapper.UserMapper;
import com.pluchon.meng.user.service.security.AuthorizationService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public final class UserQueryService {

    private final UserMapper mapper;
    private final UserConverter converter;
    private final AuthorizationService authorization;

    public UserQueryService(UserMapper mapper, UserConverter converter, AuthorizationService authorization) {
        this.mapper = mapper;
        this.converter = converter;
        this.authorization = authorization;
    }

    public UserVO me() {
        return converter.toVO(authorization.current());
    }

    public PageResult<UserVO> registrations(long page, long size, UserStatus status) {
        authorization.requireAdmin();
        var result = mapper.selectPage(
            new Page<UserEntity>(page, size),
            new LambdaQueryWrapper<UserEntity>()
                .eq(status != null, UserEntity::getStatus, status)
                .eq(UserEntity::getRole, com.pluchon.meng.user.entity.enums.UserRole.USER)
                .orderByDesc(UserEntity::getCreateTime)
                .orderByDesc(UserEntity::getId)
        );
        return new PageResult<>(
            result.getRecords().stream().map(converter::toVO).toList(),
            result.getTotal(),
            page,
            size
        );
    }

    public List<PublicUserVO> users(List<Long> ids) {
        return mapper.selectByIds(ids).stream().map(converter::toPublicVO).toList();
    }
}
