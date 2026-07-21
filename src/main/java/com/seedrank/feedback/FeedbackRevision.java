package com.seedrank.feedback;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "feedback_revisions")
public class FeedbackRevision {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feedback_id", nullable = false)
    private Feedback feedback;

    @Enumerated(EnumType.STRING)
    @Column(name = "revision_type", nullable = false, length = 20)
    private RevisionType revisionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false, length = 30)
    private Feedback.Type feedbackType;

    @Column(nullable = false, length = Feedback.MAX_CONTENT_LENGTH)
    private String content;

    @Column(name = "evidence_url", length = 2048)
    private String evidenceUrl;

    @Column(name = "evidence_description", length = 1000)
    private String evidenceDescription;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected FeedbackRevision() {
    }

    private FeedbackRevision(Feedback feedback, RevisionType revisionType, Instant recordedAt) {
        this.id = UUID.randomUUID();
        this.feedback = feedback;
        this.revisionType = revisionType;
        this.feedbackType = feedback.type();
        this.content = feedback.content();
        this.evidenceUrl = feedback.evidenceUrl();
        this.evidenceDescription = feedback.evidenceDescription();
        this.recordedAt = recordedAt;
    }

    public static FeedbackRevision snapshot(Feedback feedback, RevisionType revisionType, Instant recordedAt) {
        if (feedback == null || revisionType == null || recordedAt == null) {
            throw new IllegalArgumentException("Required revision value is missing");
        }
        return new FeedbackRevision(feedback, revisionType, recordedAt);
    }

    public enum RevisionType {
        EDITED,
        DELETED
    }
}
