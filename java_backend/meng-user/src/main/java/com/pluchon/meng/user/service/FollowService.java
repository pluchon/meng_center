package com.pluchon.meng.user.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.user.controller.vo.FollowUserVO;
import com.pluchon.meng.user.controller.vo.UserProfileVO;
import com.pluchon.meng.user.converter.UserConverter;
import com.pluchon.meng.user.entity.FollowEdge;
import com.pluchon.meng.user.entity.UserEntity;
import com.pluchon.meng.user.entity.enums.UserStatus;
import com.pluchon.meng.user.mapper.UserFollowMapper;
import com.pluchon.meng.user.mapper.UserMapper;
import com.pluchon.meng.user.mq.FollowCommitted;
import com.pluchon.meng.user.service.error.UserErrorCode;
import com.pluchon.meng.user.service.security.AuthorizationService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 关注、取消关注，以及别人的主页资料、关注和粉丝列表
@Service
public class FollowService {

    // 列表每页最多的条数
    private static final long MAX_PAGE_SIZE = 50;

    @Autowired
    private UserFollowMapper follows;

    @Autowired
    private UserMapper users;

    @Autowired
    private UserConverter converter;

    @Autowired
    private AuthorizationService authorization;

    @Autowired
    private ApplicationEventPublisher events;

    // 关注：幂等，只有第一次关注（或取消后重新关注）才加计数、发事件
    @Transactional(rollbackFor = Exception.class)
    public void follow(long target) {
        long me = authorization.current().getId();
        requireOther(me, target);
        requireNormal(users.selectById(target));
        var row = follows.lockPair(me, target);
        if (row != null && row.getDeleteState() == 0) return;
        if (row == null) follows.insertPair(me, target);
        else follows.setState(row.getId(), 0);
        adjustCounts(me, target, 1);
        events.publishEvent(new FollowCommitted(me, target));
    }

    // 取消关注：幂等，没关注过也不报错
    @Transactional(rollbackFor = Exception.class)
    public void unfollow(long target) {
        long me = authorization.current().getId();
        requireOther(me, target);
        var row = follows.lockPair(me, target);
        if (row == null || row.getDeleteState() != 0) return;
        follows.setState(row.getId(), 1);
        adjustCounts(me, target, -1);
    }

    // 别人的主页资料，带上我和他之间的关注关系
    public UserProfileVO profile(long id) {
        long me = authorization.current().getId();
        var user = requireNormal(users.selectById(id));
        var view = converter.toProfileVO(user);
        view.setSelf(me == id);
        if (me != id) {
            view.setFollowed(!follows.followedAmong(me, List.of(id)).isEmpty());
            view.setFollowsMe(!follows.followersAmong(me, List.of(id)).isEmpty());
        }
        return view;
    }

    // 某人关注的人
    public PageResult<FollowUserVO> following(long id, long page, long size) {
        checkPage(page, size);
        requireNormal(users.selectById(id));
        long total = follows.countFollowing(id);
        var edges = follows.following(id, (page - 1) * size, size);
        return new PageResult<>(items(edges), total, page, size);
    }

    // 某人的粉丝
    public PageResult<FollowUserVO> followers(long id, long page, long size) {
        checkPage(page, size);
        requireNormal(users.selectById(id));
        long total = follows.countFollowers(id);
        var edges = follows.followers(id, (page - 1) * size, size);
        return new PageResult<>(items(edges), total, page, size);
    }

    // 把一页关注边变成列表项：批量取资料，批量查我和他们的关注关系；已停用的账号不显示
    private List<FollowUserVO> items(List<FollowEdge> edges) {
        if (edges.isEmpty()) return List.of();
        long me = authorization.current().getId();
        var ids = edges.stream().map(FollowEdge::userId).toList();
        var byId = new HashMap<Long, UserEntity>();
        for (var user : users.selectByIds(ids)) byId.put(user.getId(), user);
        var followed = new HashSet<>(follows.followedAmong(me, ids));
        var followsMe = new HashSet<>(follows.followersAmong(me, ids));
        return edges
            .stream()
            .filter(edge -> {
                var user = byId.get(edge.userId());
                return user != null && user.getStatus() == UserStatus.NORMAL;
            })
            .map(edge -> {
                var view = converter.toFollowUserVO(byId.get(edge.userId()));
                view.setFollowTime(edge.createTime());
                view.setFollowed(followed.contains(edge.userId()));
                view.setFollowsMe(followsMe.contains(edge.userId()));
                view.setSelf(edge.userId() == me);
                return view;
            })
            .toList();
    }

    // 两边的计数按用户编号从小到大更新，避免互相关注时两个事务交叉加锁
    private void adjustCounts(long follower, long followee, int delta) {
        if (follower < followee) {
            users.addFollowing(follower, delta);
            users.addFollowers(followee, delta);
        } else {
            users.addFollowers(followee, delta);
            users.addFollowing(follower, delta);
        }
    }

    private static void requireOther(long me, long target) {
        if (me == target) throw new BusinessException(UserErrorCode.FOLLOW_SELF);
    }

    // 账号不存在或不是正常状态都按不存在处理
    private static UserEntity requireNormal(UserEntity user) {
        if (user == null || user.getStatus() != UserStatus.NORMAL) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return user;
    }

    private static void checkPage(long page, long size) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        }
    }
}
