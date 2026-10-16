package com.pluchon.meng.content.mq;

import com.pluchon.meng.content.controller.dto.PostAnalysisDTO;
import com.pluchon.meng.content.service.AiResultService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// AI 结果事件的解析与域内分发
@Component
public class AiResultListener {

    // 对应领域业务操作
    @Autowired
    private AiResultService service;

    // 复用 JSON 编解码器
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    @RabbitListener(queues = "meng.content.post.analyzed")
    public void post(Message message) throws java.io.IOException {
        var event = json.readTree(message.getBody());
        if (
            !"post.analyzed".equals(event.path("eventType").asString())
        ) throw new IllegalArgumentException("Unexpected event");
        service.post(
            event.path("eventId").asString(),
            json.treeToValue(event.path("body"), PostAnalysisDTO.class)
        );
    }

    @RabbitListener(queues = "meng.content.comment.moderated")
    public void comment(Message message) {
        var event = json.readTree(message.getBody());
        if (
            !"comment.moderated".equals(event.path("eventType").asString())
        ) throw new IllegalArgumentException("Unexpected event");
        service.comment(
            event.path("eventId").asString(),
            json.convertValue(
                event.path("body"),
                new tools.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {}
            )
        );
    }
}
