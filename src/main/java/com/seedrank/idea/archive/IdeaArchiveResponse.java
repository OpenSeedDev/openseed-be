package com.seedrank.idea.archive;

import java.time.Instant;
import java.util.UUID;

import com.seedrank.idea.Idea;
import com.seedrank.idea.IdeaStatus;

record IdeaArchiveResponse(UUID id, IdeaStatus status, Instant updatedAt) {
    static IdeaArchiveResponse from(Idea idea) {
        return new IdeaArchiveResponse(idea.id(), idea.status(), idea.updatedAt());
    }
}
