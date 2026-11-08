package com.pluchon.meng.im.realtime;

import com.pluchon.meng.im.mapper.ImMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.json.JsonMapper;

// 每个标签页一条连接，只处理心跳和在线状态订阅
@Component
public class ImSocket extends TextWebSocketHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ImSocket.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final Map<String, Connection> connections =
        new ConcurrentHashMap<>();
    private final Map<Long, Long> offlineAt = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> hidden = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timer =
        Executors.newSingleThreadScheduledExecutor(task -> {
            var thread = new Thread(task, "im-connections");
            thread.setDaemon(true);
            return thread;
        });

    @Autowired
    private ImMapper mapper;

    // 会话被撤销：前端不再重连，转去重新登录
    public static final CloseStatus SESSION_REVOKED = new CloseStatus(4401, "session revoked");

    // 到达最长存活时间：前端照常重连，握手时由网关重新校验会话
    private static final CloseStatus REAUTHENTICATE = new CloseStatus(4000, "reauthenticate");

    // 连接最长存活时间，撤销事件丢失时也能在这段时间内收回
    private static final long MAX_AGE_MILLIS = TimeUnit.MINUTES.toMillis(30);

    // 连接自己的身份和单个订阅对象
    private static final class Connection {

        final WebSocketSession session;
        final long user;
        final String loginSession;
        final long openedAt = System.currentTimeMillis();
        volatile long heartbeat = openedAt;
        volatile Long watching;

        Connection(WebSocketSession session, long user, String loginSession) {
            this.session = session;
            this.user = user;
            this.loginSession = loginSession;
        }
    }

    public ImSocket() {
        timer.scheduleWithFixedDelay(
            () -> {
                try {
                    sweep();
                } catch (RuntimeException unavailable) {
                    LOG.warn("IM connection maintenance will retry");
                }
            },
            5,
            5,
            TimeUnit.SECONDS
        );
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        long user = (Long) session.getAttributes().get("userId");
        boolean wasOnline = online(user);
        hidden.put(user, Boolean.TRUE.equals(mapper.hidden(user)));
        connections.put(
            session.getId(),
            new Connection(
                new ConcurrentWebSocketSessionDecorator(session, 3000, 65536),
                user,
                (String) session.getAttributes().get("sessionId")
            )
        );
        offlineAt.remove(user);
        if (!wasOnline) changed(user);
    }

    @Override
    protected void handleTextMessage(
        WebSocketSession session,
        TextMessage message
    ) throws Exception {
        var connection = connections.get(session.getId());
        if (connection == null) return;
        var frame = JSON.readTree(message.getPayload());
        String type = frame.path("type").asString();
        if (type.equals("ping")) {
            connection.heartbeat = System.currentTimeMillis();
            send(connection, "pong", Map.of());
        } else if (type.equals("presence.watch")) {
            long peer = frame.path("data").path("userId").asLong();
            if (peer < 1 || peer == connection.user) {
                session.close(CloseStatus.BAD_DATA);
                return;
            }
            connection.watching = peer;
            send(
                connection,
                "presence",
                Map.of("userId", peer, "online", online(peer))
            );
        } else if (type.equals("presence.unwatch")) connection.watching = null;
        else session.close(CloseStatus.BAD_DATA);
    }

    @Override
    public void afterConnectionClosed(
        WebSocketSession session,
        CloseStatus status
    ) {
        var removed = connections.remove(session.getId());
        if (
            removed != null &&
            connections
                .values()
                .stream()
                .noneMatch(value -> value.user == removed.user)
        ) {
            offlineAt.put(removed.user, System.currentTimeMillis() + 10000);
        }
    }

    @Override
    public void handleTransportError(
        WebSocketSession session,
        Throwable failure
    ) throws Exception {
        session.close(CloseStatus.SERVER_ERROR);
    }

    public boolean online(long user) {
        if (Boolean.TRUE.equals(hidden.get(user))) return false;
        return (
            connections
                .values()
                .stream()
                .anyMatch(value -> value.user == user) ||
            offlineAt.getOrDefault(user, 0L) > System.currentTimeMillis()
        );
    }

    public void changed(long user) {
        hidden.put(user, Boolean.TRUE.equals(mapper.hidden(user)));
        boolean online = online(user);
        connections
            .values()
            .stream()
            .filter(value -> Long.valueOf(user).equals(value.watching))
            .forEach(value ->
                send(
                    value,
                    "presence",
                    Map.of("userId", user, "online", online)
                )
            );
    }

    public void push(long user, String type, Object data) {
        connections
            .values()
            .stream()
            .filter(value -> value.user == user)
            .forEach(value -> send(value, type, data));
    }

    private void send(Connection value, String type, Object data) {
        try {
            if (value.session.isOpen()) value.session.sendMessage(
                new TextMessage(
                    JSON.writeValueAsString(Map.of("type", type, "data", data))
                )
            );
        } catch (IOException | RuntimeException failure) {
            close(value);
        }
    }

    // 断开属于这些登录会话的连接
    public void revoke(long user, Predicate<String> covered) {
        connections
            .values()
            .stream()
            .filter(value -> value.user == user && covered.test(value.loginSession))
            .forEach(value -> close(value, SESSION_REVOKED));
    }

    private void close(Connection value) {
        close(value, CloseStatus.SERVER_ERROR);
    }

    private void close(Connection value, CloseStatus status) {
        try {
            value.session.close(status);
        } catch (IOException ignored) {}
        afterConnectionClosed(value.session, status);
    }

    private void sweep() {
        long now = System.currentTimeMillis();
        connections
            .values()
            .stream()
            .filter(value -> now - value.heartbeat > 60000)
            .forEach(this::close);
        connections
            .values()
            .stream()
            .filter(value -> now - value.openedAt > MAX_AGE_MILLIS)
            .forEach(value -> close(value, REAUTHENTICATE));
        offlineAt.forEach((user, time) -> {
            if (time <= now && offlineAt.remove(user, time)) {
                changed(user);
                hidden.remove(user);
            }
        });
    }

    @PreDestroy
    public void stop() {
        timer.shutdownNow();
        connections.values().forEach(this::close);
        connections.clear();
    }
}
