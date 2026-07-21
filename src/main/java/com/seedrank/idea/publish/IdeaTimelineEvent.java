package com.seedrank.idea.publish;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "idea_timeline_events")
class IdeaTimelineEvent {
    @Id private UUID id;
    @Column(name = "idea_id", nullable = false) private UUID ideaId;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 30) private Type eventType;
    @Column(name = "actor_id", nullable = false) private UUID actorId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected IdeaTimelineEvent() {}

    static IdeaTimelineEvent published(UUID ideaId, UUID actorId, Instant now) {
        IdeaTimelineEvent event = new IdeaTimelineEvent();
        event.id = UUID.randomUUID();
        event.ideaId = ideaId;
        event.eventType = Type.PUBLISHED;
        event.actorId = actorId;
        event.createdAt = now;
        return event;
    }

    enum Type { PUBLISHED }
}
