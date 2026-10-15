package com.pluchon.meng.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.user.entity.UserEntity;
import com.pluchon.meng.user.entity.UserFollowEntity;
import com.pluchon.meng.user.entity.enums.UserStatus;
import com.pluchon.meng.user.mapper.UserFollowMapper;
import com.pluchon.meng.user.mapper.UserMapper;
import com.pluchon.meng.user.mq.FollowCommitted;
import com.pluchon.meng.user.service.FollowService;
import com.pluchon.meng.user.service.error.UserErrorCode;
import com.pluchon.meng.user.service.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class FollowServiceTest {

    private final UserFollowMapper follows = mock(UserFollowMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final FollowService service = new FollowService();

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "follows", follows);
        ReflectionTestUtils.setField(service, "users", users);
        ReflectionTestUtils.setField(service, "authorization", authorization);
        ReflectionTestUtils.setField(service, "events", events);
        when(authorization.current()).thenReturn(user(9, UserStatus.NORMAL));
        when(users.selectById(3L)).thenReturn(user(3, UserStatus.NORMAL));
    }

    private static UserEntity user(long id, UserStatus status) {
        var user = new UserEntity();
        user.setId(id);
        user.setStatus(status);
        return user;
    }

    private static UserFollowEntity row(long id, int deleteState) {
        var row = new UserFollowEntity();
        row.setId(id);
        row.setDeleteState(deleteState);
        return row;
    }

    @Test
    void firstFollowInsertsCountsAndPublishes() {
        service.follow(3);
        verify(follows).insertPair(9, 3);
        verify(users).addFollowing(9, 1);
        verify(users).addFollowers(3, 1);
        verify(events).publishEvent(new FollowCommitted(9, 3));
    }

    @Test
    void countsAreUpdatedInUserIdOrder() {
        service.follow(3);
        var order = inOrder(users);
        order.verify(users).addFollowers(3, 1);
        order.verify(users).addFollowing(9, 1);
    }

    @Test
    void followingAgainDoesNothing() {
        when(follows.lockPair(9, 3)).thenReturn(row(5, 0));
        service.follow(3);
        verify(follows, never()).insertPair(9, 3);
        verify(users, never()).addFollowing(9, 1);
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void followingAfterUnfollowRestoresTheRow() {
        when(follows.lockPair(9, 3)).thenReturn(row(5, 1));
        service.follow(3);
        verify(follows).setState(5, 0);
        verify(follows, never()).insertPair(9, 3);
        verify(users).addFollowing(9, 1);
        verify(events).publishEvent(new FollowCommitted(9, 3));
    }

    @Test
    void cannotFollowSelfOrAnInactiveAccount() {
        var self = assertThrows(BusinessException.class, () -> service.follow(9));
        assertEquals(UserErrorCode.FOLLOW_SELF, self.errorCode());
        when(users.selectById(4L)).thenReturn(user(4, UserStatus.PENDING));
        assertThrows(BusinessException.class, () -> service.follow(4));
        assertThrows(BusinessException.class, () -> service.follow(404));
        verify(follows, never()).insertPair(9, 4);
    }

    @Test
    void unfollowFlipsTheRowAndNeverNotifies() {
        when(follows.lockPair(9, 3)).thenReturn(row(5, 0));
        service.unfollow(3);
        verify(follows).setState(5, 1);
        verify(users).addFollowing(9, -1);
        verify(users).addFollowers(3, -1);
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void unfollowingSomeoneNotFollowedIsHarmless() {
        service.unfollow(3);
        when(follows.lockPair(9, 3)).thenReturn(row(5, 1));
        service.unfollow(3);
        verify(follows, never()).setState(5, 1);
        verify(users, never()).addFollowing(9, -1);
    }
}
