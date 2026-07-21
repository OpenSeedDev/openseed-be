package com.seedrank.messaging.thread;

import java.time.Instant;
import java.util.UUID;

record MessageThreadResponse(UUID id, UUID ideaId, Instant createdAt) {
    static MessageThreadResponse from(MessageThread thread) {
        return new MessageThreadResponse(thread.id(), thread.ideaId(), thread.createdAt());
    }
}
