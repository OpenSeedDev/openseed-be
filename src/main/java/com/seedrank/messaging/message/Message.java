package com.seedrank.messaging.message;

import java.time.Instant;
import java.util.UUID;

import com.seedrank.messaging.thread.MessageThread;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "message_thread_messages")
class Message {
    static final int MAX_CONTENT_LENGTH = 2_000;

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "thread_id", nullable = false)
    private MessageThread thread;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected Message() {
    }

    private Message(MessageThread thread, UUID senderId, String content, Instant sentAt) {
        this.id = UUID.randomUUID();
        this.thread = thread;
        this.senderId = senderId;
        this.content = normalize(content);
        this.sentAt = sentAt;
    }

    static Message send(MessageThread thread, UUID senderId, String content, Instant sentAt) {
        if (thread == null || senderId == null || sentAt == null) {
            throw new IllegalArgumentException("Required message value is missing");
        }
        return new Message(thread, senderId, content, sentAt);
    }

    private static String normalize(String value) {
        if (value == null) throw new IllegalArgumentException("Message content is required");
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Message content length is invalid");
        }
        return normalized;
    }

    UUID id() { return id; }
    UUID senderId() { return senderId; }
    String content() { return content; }
    Instant sentAt() { return sentAt; }
}
