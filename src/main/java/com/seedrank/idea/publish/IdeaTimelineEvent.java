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
public class IdeaTimelineEvent {
    @Id private UUID id;
    @Column(name = "idea_id", nullable = false) private UUID ideaId;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 30) private Type eventType;
    @Column(name = "actor_id", nullable = false) private UUID actorId;
    @Column(name = "source_id") private UUID sourceId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected IdeaTimelineEvent() {}

    public static IdeaTimelineEvent published(UUID ideaId, UUID actorId, Instant now) {
        return event(ideaId, actorId, Type.PUBLISHED, now);
    }

    public static IdeaTimelineEvent updated(UUID ideaId, UUID actorId, Instant now) {
        return event(ideaId, actorId, Type.UPDATED, now);
    }

    public static IdeaTimelineEvent feedbackAccepted(
            UUID ideaId, UUID contributorId, UUID feedbackId, Instant now) {
        IdeaTimelineEvent event = event(ideaId, contributorId, Type.FEEDBACK_ACCEPTED, now);
        event.sourceId = feedbackId;
        return event;
    }

    private static IdeaTimelineEvent event(UUID ideaId, UUID actorId, Type type, Instant now) {
        IdeaTimelineEvent event = new IdeaTimelineEvent();
        event.id = UUID.randomUUID();
        event.ideaId = ideaId;
        event.eventType = type;
        event.actorId = actorId;
        event.createdAt = now;
        return event;
    }

    public UUID id() { return id; }
    public UUID ideaId() { return ideaId; }
    public Type eventType() { return eventType; }
    public UUID actorId() { return actorId; }
    public UUID sourceId() { return sourceId; }
    public Instant createdAt() { return createdAt; }

    public enum Type { PUBLISHED, UPDATED, FEEDBACK_ACCEPTED }
}
