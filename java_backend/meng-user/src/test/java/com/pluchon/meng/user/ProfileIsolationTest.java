package com.pluchon.meng.user;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pluchon.meng.user.config.ProfileJobConfiguration;
import com.pluchon.meng.user.entity.UserEntity;
import com.pluchon.meng.user.service.ProfileModerationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class ProfileIsolationTest {

    @Test
    void isolatedContextDoesNotCreateBusinessJobExecutor() {
        new ApplicationContextRunner()
            .withPropertyValues("meng.user.profile-moderation.enabled=false")
            .withUserConfiguration(ProfileJobConfiguration.class)
            .run(context -> {
                assertNull(context.getStartupFailure());
                assertFalse(context.containsBean("profileJobExecutor"));
            });
    }

    @Test
    void isolatedStartupAndProfileChangesNeverReadBusinessOutbox() {
        var service = new ProfileModerationService();
        var jdbc = mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "enabled", false);
        service.ready();
        service.updated(new UserEntity(), false);
        verifyNoInteractions(jdbc);
    }
}
