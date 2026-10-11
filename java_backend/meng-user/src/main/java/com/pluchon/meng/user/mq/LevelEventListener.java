package com.pluchon.meng.user.mq;

import com.pluchon.meng.api.growth.event.LevelChangedEvent;
import com.pluchon.meng.common.core.event.EventEnvelope;
import com.pluchon.meng.user.service.UserLevelService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LevelEventListener {

    public static final String QUEUE = "meng.user.growth.level-changed";
    private final UserLevelService levels;

    @RabbitListener(queues = QUEUE)
    public void changed(EventEnvelope<LevelChangedEvent> e) {
        if (
            !LevelChangedEvent.EVENT_TYPE.equals(e.eventType())
        ) throw new IllegalArgumentException("Wrong event type");
        levels.update(e.body().userId(), e.body().level());
    }
}
