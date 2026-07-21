package com.seedrank.idea.like;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class IdeaLikeQuery {
    private final IdeaLikeRepository likes;

    IdeaLikeQuery(IdeaLikeRepository likes) {
        this.likes = likes;
    }

    public long count(UUID ideaId) {
        return likes.countByIdeaId(ideaId);
    }

    public boolean likedBy(UUID ideaId, UUID userId) {
        return likes.existsByIdeaIdAndUserId(ideaId, userId);
    }
}
