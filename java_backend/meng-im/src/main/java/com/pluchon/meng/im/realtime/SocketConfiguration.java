package com.pluchon.meng.im.realtime;

import com.pluchon.meng.common.core.security.IdentityHeaders;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

// WebSocket 握手沿用网关注入的身份，限制跨站连接
@Configuration
@EnableWebSocket
public class SocketConfiguration implements WebSocketConfigurer {

    @Autowired
    private ImSocket socket;

    @Value("${meng.im.allowed-origins}")
    private String[] origins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
            .addHandler(socket, "/im/ws")
            .setAllowedOrigins(origins)
            .addInterceptors(
                new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(
                        ServerHttpRequest request,
                        ServerHttpResponse response,
                        WebSocketHandler handler,
                        Map<String, Object> attributes
                    ) {
                        try {
                            long user = Long.parseLong(
                                request
                                    .getHeaders()
                                    .getFirst(IdentityHeaders.USER_ID)
                            );
                            if (
                                user < 1 ||
                                request
                                    .getHeaders()
                                    .getFirst(IdentityHeaders.USER_ROLE) == null
                            ) throw new IllegalArgumentException();
                            attributes.put("userId", user);
                            return true;
                        } catch (RuntimeException invalid) {
                            response.setStatusCode(HttpStatus.UNAUTHORIZED);
                            return false;
                        }
                    }

                    @Override
                    public void afterHandshake(
                        ServerHttpRequest request,
                        ServerHttpResponse response,
                        WebSocketHandler handler,
                        Exception failure
                    ) {}
                }
            );
    }
}
