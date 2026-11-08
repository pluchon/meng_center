package com.pluchon.meng.common.core.error;

import java.util.Set;

// 判断异常是否来自依赖暂时不可用（数据库、Redis、消息队列连不上或超时、下游服务连不上），按类名匹配，不引入各中间件的依赖
public final class DependencyFailures {

    // 表示依赖暂时不可用的异常类型，子类同样算
    private static final Set<String> UNAVAILABLE = Set.of(
        "org.springframework.dao.DataAccessResourceFailureException",
        "org.springframework.dao.QueryTimeoutException",
        "org.springframework.transaction.CannotCreateTransactionException",
        "org.springframework.amqp.AmqpConnectException",
        "org.springframework.amqp.AmqpTimeoutException",
        "feign.RetryableException",
        "java.net.ConnectException",
        "java.util.concurrent.TimeoutException"
    );

    private DependencyFailures() {}

    // 异常本身或它的任一层原因属于依赖不可用时返回 true
    public static boolean unavailable(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            for (Class<?> type = current.getClass(); type != null; type = type.getSuperclass()) {
                if (UNAVAILABLE.contains(type.getName())) return true;
            }
            if (current.getCause() == current) break;
        }
        return false;
    }
}
