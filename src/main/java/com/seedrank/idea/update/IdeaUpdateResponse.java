package com.seedrank.idea.update;

import java.time.Instant;
import java.util.UUID;

import com.seedrank.idea.Idea;
import com.seedrank.idea.IdeaStatus;
import com.seedrank.idea.IdeaVisibility;

public record IdeaUpdateResponse(
        UUID id,
        IdeaStatus status,
        String title,
        String category,
        String summary,
        String problem,
        String targetCustomer,
        String solution,
        String businessModel,
        IdeaVisibility visibility,
        Instant updatedAt) {

    static IdeaUpdateResponse from(Idea idea) {
        return new IdeaUpdateResponse(
                idea.id(), idea.status(), idea.title(), idea.category(), idea.summary(), idea.problem(),
                idea.targetCustomer(), idea.solution(), idea.businessModel(), idea.visibility(), idea.updatedAt());
    }
}
