package com.pluchon.meng.common.core.event;

import java.time.Instant;
import java.util.Objects;

public record EventEnvelope<T>(String eventId, String eventType, Instant occurredAt, String traceId, T body) {
    public EventEnvelope {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(traceId);
        Objects.requireNonNull(body);
    }
}
