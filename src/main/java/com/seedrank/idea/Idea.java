package com.seedrank.idea;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ideas")
public class Idea {

    @Id
    private UUID id;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdeaStatus status;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(length = 200)
    private String summary;

    @Column(nullable = false, length = 2000)
    private String problem;

    @Column(name = "target_customer", length = 1000)
    private String targetCustomer;

    @Column(length = 2000)
    private String solution;

    @Column(name = "business_model", length = 2000)
    private String businessModel;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private IdeaVisibility visibility;

    @Column(name = "current_unit_price")
    private Integer currentUnitPrice;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Idea() {
    }

    private Idea(
            UUID authorId,
            String title,
            String category,
            String summary,
            String problem,
            String targetCustomer,
            String solution,
            String businessModel,
            Instant now) {
        this.id = UUID.randomUUID();
        this.authorId = authorId;
        this.status = IdeaStatus.DRAFT;
        this.title = required(title);
        this.category = required(category);
        this.summary = optional(summary);
        this.problem = required(problem);
        this.targetCustomer = optional(targetCustomer);
        this.solution = optional(solution);
        this.businessModel = optional(businessModel);
        this.createdAt = now;
        this.updatedAt = now;
    }

    static Idea draft(
            UUID authorId,
            String title,
            String category,
            String summary,
            String problem,
            String targetCustomer,
            String solution,
            String businessModel,
            Instant now) {
        return new Idea(authorId, title, category, summary, problem, targetCustomer, solution, businessModel, now);
    }

    private static String required(String value) {
        return value.strip();
    }

    private static String optional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    public UUID id() { return id; }
    public UUID authorId() { return authorId; }
    public IdeaStatus status() { return status; }
    public String title() { return title; }
    public String category() { return category; }
    public String summary() { return summary; }
    public String problem() { return problem; }
    public String targetCustomer() { return targetCustomer; }
    public String solution() { return solution; }
    public String businessModel() { return businessModel; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public IdeaVisibility visibility() { return visibility; }
    public Integer currentUnitPrice() { return currentUnitPrice; }
    public Instant publishedAt() { return publishedAt; }

    public void publish(IdeaVisibility visibility, Instant now) {
        if (status != IdeaStatus.DRAFT) {
            throw new IllegalStateException("Idea is not a draft");
        }
        if (!complete()) {
            throw new IllegalArgumentException("Idea content is incomplete");
        }
        this.status = IdeaStatus.PUBLISHED;
        this.visibility = visibility;
        this.currentUnitPrice = 10;
        this.publishedAt = now;
        this.updatedAt = now;
    }

    public void updatePublishedContent(
            String title,
            String category,
            String summary,
            String problem,
            String targetCustomer,
            String solution,
            String businessModel,
            Instant now) {
        if (status != IdeaStatus.PUBLISHED) {
            throw new IllegalStateException("Idea is not published");
        }
        this.title = required(title);
        this.category = required(category);
        this.summary = required(summary);
        this.problem = required(problem);
        this.targetCustomer = required(targetCustomer);
        this.solution = required(solution);
        this.businessModel = required(businessModel);
        this.updatedAt = now;
    }

    private boolean complete() {
        return present(title) && present(category) && present(summary) && present(problem)
                && present(targetCustomer) && present(solution) && present(businessModel);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
