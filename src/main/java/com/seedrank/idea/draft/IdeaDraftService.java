package com.seedrank.idea.draft;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.idea.Idea;
import com.seedrank.idea.IdeaRepository;

@Service
class IdeaDraftService {
    private final AccessTokenAuthenticator authenticator;
    private final IdeaRepository ideas;
    private final Clock clock;

    IdeaDraftService(AccessTokenAuthenticator authenticator, IdeaRepository ideas, Clock clock) {
        this.authenticator = authenticator;
        this.ideas = ideas;
        this.clock = clock;
    }

    @Transactional
    IdeaDraftResponse create(String authorization, IdeaDraftRequest request) {
        UUID authorId = authenticator.authenticate(authorization).userId();
        Idea idea = Idea.draft(
                authorId,
                request.title(),
                request.category(),
                request.summary(),
                request.problem(),
                request.targetCustomer(),
                request.solution(),
                request.businessModel(),
                clock.instant());
        return IdeaDraftResponse.from(ideas.save(idea));
    }

    @Transactional(readOnly = true)
    IdeaDraftResponse get(String authorization, UUID ideaId) {
        UUID authorId = authenticator.authenticate(authorization).userId();
        return ideas.findByIdAndAuthorId(ideaId, authorId)
                .map(IdeaDraftResponse::from)
                .orElseThrow(IdeaDraftNotFoundException::new);
    }
}
