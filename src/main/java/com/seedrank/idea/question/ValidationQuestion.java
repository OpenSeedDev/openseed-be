package com.seedrank.idea.question;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "validation_questions")
public class ValidationQuestion {

    @Id
    private UUID id;

    @Column(name = "idea_id", nullable = false)
    private UUID ideaId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ValidationQuestion() {
    }

    private ValidationQuestion(UUID ideaId, String question, int sortOrder) {
        this.id = UUID.randomUUID();
        this.ideaId = ideaId;
        this.question = question.strip();
        this.sortOrder = sortOrder;
    }

    static ValidationQuestion create(UUID ideaId, String question, int sortOrder) {
        return new ValidationQuestion(ideaId, question, sortOrder);
    }

    public UUID id() { return id; }
    public UUID ideaId() { return ideaId; }
    public String question() { return question; }
    public int sortOrder() { return sortOrder; }
}
