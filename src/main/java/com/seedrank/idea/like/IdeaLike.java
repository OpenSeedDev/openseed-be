package com.seedrank.idea.like;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "idea_likes")
class IdeaLike {
    @Id
    private UUID id;

    @Column(name = "idea_id", nullable = false)
    private UUID ideaId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdeaLike() {
    }

    private IdeaLike(UUID ideaId, UUID userId, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.ideaId = ideaId;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    static IdeaLike create(UUID ideaId, UUID userId, Instant createdAt) {
        if (ideaId == null || userId == null || createdAt == null) {
            throw new IllegalArgumentException("Required like value is missing");
        }
        return new IdeaLike(ideaId, userId, createdAt);
    }
}
