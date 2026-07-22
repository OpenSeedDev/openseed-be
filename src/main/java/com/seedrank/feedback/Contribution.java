package com.seedrank.feedback;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "contributions")
public class Contribution {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_feedback_id", nullable = false, unique = true)
    private Feedback sourceFeedback;
    @Column(name = "idea_id", nullable = false) private UUID ideaId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected Contribution() {}

    public static Contribution from(Feedback feedback, Instant now) {
        if (feedback == null || now == null) throw new IllegalArgumentException("Contribution value is missing");
        Contribution contribution = new Contribution();
        contribution.id = UUID.randomUUID();
        contribution.sourceFeedback = feedback;
        contribution.ideaId = feedback.ideaId();
        contribution.userId = feedback.authorId();
        contribution.createdAt = now;
        return contribution;
    }

    public UUID id() { return id; }
    public UUID ideaId() { return ideaId; }
    public UUID userId() { return userId; }
    public Instant createdAt() { return createdAt; }
}
