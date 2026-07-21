package com.seedrank.idea.draft;

import java.time.Instant;
import java.util.UUID;

import com.seedrank.idea.Idea;
import com.seedrank.idea.IdeaStatus;

public record IdeaDraftResponse(
        UUID id,
        IdeaStatus status,
        String title,
        String category,
        String summary,
        String problem,
        String targetCustomer,
        String solution,
        String businessModel,
        Instant createdAt,
        Instant updatedAt) {

    static IdeaDraftResponse from(Idea idea) {
        return new IdeaDraftResponse(
                idea.id(), idea.status(), idea.title(), idea.category(), idea.summary(), idea.problem(),
                idea.targetCustomer(), idea.solution(), idea.businessModel(), idea.createdAt(), idea.updatedAt());
    }
}
