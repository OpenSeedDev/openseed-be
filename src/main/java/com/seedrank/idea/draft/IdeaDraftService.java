package com.seedrank.idea.draft;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.idea.Idea;
import com.seedrank.idea.IdeaDraftFactory;
import com.seedrank.idea.IdeaRepository;

@Service
class IdeaDraftService {
    private final AccessTokenAuthenticator authenticator;
    private final IdeaRepository ideas;
    private final IdeaDraftFactory ideaDraftFactory;

    IdeaDraftService(
            AccessTokenAuthenticator authenticator,
            IdeaRepository ideas,
            IdeaDraftFactory ideaDraftFactory) {
        this.authenticator = authenticator;
        this.ideas = ideas;
        this.ideaDraftFactory = ideaDraftFactory;
    }

    @Transactional
    IdeaDraftResponse create(String authorization, IdeaDraftRequest request) {
        UUID authorId = authenticator.authenticate(authorization).userId();
        Idea idea = ideaDraftFactory.create(
                authorId,
                request.title(),
                request.category(),
                request.summary(),
                request.problem(),
                request.targetCustomer(),
                request.solution(),
                request.businessModel());
        return IdeaDraftResponse.from(ideas.save(idea), List.of());
    }

}
