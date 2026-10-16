package com.pluchon.meng.user.mq;

import com.pluchon.meng.user.service.ProfileModerationService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// 资料审核结果事件消费入口
@Component
public class ProfileReviewListener {

    // 对应领域业务操作
    @Autowired
    private ProfileModerationService service;

    // 复用 JSON 编解码器
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    @RabbitListener(queues = "meng.user.user.profile-moderated")
    public void result(Message message) {
        var event = json.readTree(message.getBody());
        if (
            !"user.profile-moderated".equals(event.path("eventType").asString())
        ) throw new IllegalArgumentException("Unexpected event");
        service.result(
            json.convertValue(
                event.path("body"),
                new tools.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {}
            )
        );
    }
}
