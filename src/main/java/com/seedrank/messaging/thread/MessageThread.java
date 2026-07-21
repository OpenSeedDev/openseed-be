package com.seedrank.messaging.thread;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "message_threads")
class MessageThread {

    @Id
    private UUID id;

    @Column(name = "idea_id", nullable = false)
    private UUID ideaId;

    @Column(name = "company_profile_id", nullable = false)
    private UUID companyProfileId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MessageThread() {
    }

    private MessageThread(UUID ideaId, UUID companyProfileId, UUID authorId, Instant now) {
        this.id = UUID.randomUUID();
        this.ideaId = ideaId;
        this.companyProfileId = companyProfileId;
        this.authorId = authorId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    static MessageThread start(UUID ideaId, UUID companyProfileId, UUID authorId, Instant now) {
        return new MessageThread(ideaId, companyProfileId, authorId, now);
    }

    UUID id() { return id; }
    UUID ideaId() { return ideaId; }
    Instant createdAt() { return createdAt; }
}
