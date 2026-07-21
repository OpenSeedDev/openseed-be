package com.seedrank.idea.me;

import java.time.Instant;
import java.util.UUID;

import com.seedrank.idea.Idea;
import com.seedrank.idea.IdeaStatus;

record MyIdeaItemResponse(
        UUID id,
        IdeaStatus status,
        String title,
        String category,
        String summary,
        Instant createdAt,
        Instant updatedAt) {

    static MyIdeaItemResponse from(Idea idea) {
        return new MyIdeaItemResponse(
                idea.id(), idea.status(), idea.title(), idea.category(), idea.summary(),
                idea.createdAt(), idea.updatedAt());
    }
}
