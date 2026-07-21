package com.seedrank.idea.like;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.idea.Idea;
import com.seedrank.idea.IdeaRepository;
import com.seedrank.idea.IdeaStatus;
import com.seedrank.idea.draft.IdeaDraftNotFoundException;

@Service
class IdeaLikeService {
    private final AccessTokenAuthenticator authenticator;
    private final IdeaRepository ideas;
    private final IdeaLikeRepository likes;
    private final Clock clock;

    IdeaLikeService(
            AccessTokenAuthenticator authenticator,
            IdeaRepository ideas,
            IdeaLikeRepository likes,
            Clock clock) {
        this.authenticator = authenticator;
        this.ideas = ideas;
        this.likes = likes;
        this.clock = clock;
    }

    @Transactional
    IdeaLikeResponse like(String authorization, UUID ideaId) {
        UUID userId = authenticator.authenticate(authorization).userId();
        publishedIdeaForUpdate(ideaId);
        if (!likes.existsByIdeaIdAndUserId(ideaId, userId)) {
            likes.saveAndFlush(IdeaLike.create(ideaId, userId, clock.instant()));
        }
        return new IdeaLikeResponse(true, likes.countByIdeaId(ideaId));
    }

    @Transactional
    IdeaLikeResponse unlike(String authorization, UUID ideaId) {
        UUID userId = authenticator.authenticate(authorization).userId();
        publishedIdeaForUpdate(ideaId);
        likes.deleteByIdeaIdAndUserId(ideaId, userId);
        return new IdeaLikeResponse(false, likes.countByIdeaId(ideaId));
    }

    private Idea publishedIdeaForUpdate(UUID ideaId) {
        Idea idea = ideas.findByIdForUpdate(ideaId).orElseThrow(IdeaDraftNotFoundException::new);
        if (idea.status() != IdeaStatus.PUBLISHED) throw new IdeaDraftNotFoundException();
        return idea;
    }
}
