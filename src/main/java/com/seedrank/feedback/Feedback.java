package com.seedrank.feedback;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import com.seedrank.idea.Idea;
import com.seedrank.member.User;

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
@Table(name = "feedbacks")
public class Feedback {
    public static final int MIN_CONTENT_LENGTH = 100;
    public static final int MAX_CONTENT_LENGTH = 2_000;

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idea_id", nullable = false)
    private Idea idea;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false, length = 30)
    private Type type;

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @Column(name = "evidence_url", length = 2048)
    private String evidenceUrl;

    @Column(name = "evidence_description", length = 1000)
    private String evidenceDescription;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Feedback() {
    }

    private Feedback(Idea idea, User author, Type type, String content,
            String evidenceUrl, String evidenceDescription, Instant now) {
        this.id = UUID.randomUUID();
        this.idea = idea;
        this.author = author;
        this.type = type;
        this.content = normalizeContent(content);
        this.evidenceUrl = normalizeUrl(evidenceUrl);
        this.evidenceDescription = normalizeOptional(evidenceDescription, 1_000);
        this.createdAt = now;
    }

    public static Feedback create(Idea idea, User author, Type type, String content,
            String evidenceUrl, String evidenceDescription, Instant now) {
        if (idea == null || author == null || type == null || now == null) {
            throw new IllegalArgumentException("Required feedback value is missing");
        }
        return new Feedback(idea, author, type, content, evidenceUrl, evidenceDescription, now);
    }

    private static String normalizeContent(String value) {
        if (value == null) throw new IllegalArgumentException("Feedback content is required");
        String normalized = value.strip();
        if (normalized.length() < MIN_CONTENT_LENGTH || normalized.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Feedback content length is invalid");
        }
        return normalized;
    }

    private static String normalizeUrl(String value) {
        String normalized = normalizeOptional(value, 2_048);
        if (normalized == null) return null;
        try {
            URI uri = URI.create(normalized);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                throw new IllegalArgumentException("Evidence URL must use HTTP or HTTPS");
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Evidence URL is invalid", exception);
        }
    }

    private static String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip();
        if (normalized.length() > maxLength) throw new IllegalArgumentException("Optional value is too long");
        return normalized;
    }

    public UUID id() { return id; }
    public UUID ideaId() { return idea.id(); }
    public UUID authorId() { return author.getId(); }
    public Type type() { return type; }
    public String content() { return content; }
    public String evidenceUrl() { return evidenceUrl; }
    public String evidenceDescription() { return evidenceDescription; }
    public Instant acceptedAt() { return acceptedAt; }
    public Instant editedAt() { return editedAt; }
    public Instant deletedAt() { return deletedAt; }
    public Instant createdAt() { return createdAt; }

    public enum Type {
        PROBLEM_EMPATHY,
        TARGET_CUSTOMER,
        SOLUTION,
        BUSINESS_MODEL,
        COMPETITION,
        OTHER
    }
}
