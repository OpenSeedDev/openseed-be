package com.seedrank.idea.archive;

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
class IdeaArchiveService {
    private final AccessTokenAuthenticator authenticator;
    private final IdeaRepository ideas;
    private final Clock clock;

    IdeaArchiveService(AccessTokenAuthenticator authenticator, IdeaRepository ideas, Clock clock) {
        this.authenticator = authenticator;
        this.ideas = ideas;
        this.clock = clock;
    }

    @Transactional
    IdeaArchiveResponse archive(String authorization, UUID ideaId) {
        UUID authorId = authenticator.authenticate(authorization).userId();
        Idea idea = ideas.findByIdAndAuthorIdForUpdate(ideaId, authorId)
                .orElseThrow(IdeaDraftNotFoundException::new);
        if (idea.status() != IdeaStatus.PUBLISHED) {
            throw new IdeaNotArchivableException();
        }
        idea.archive(clock.instant());
        return IdeaArchiveResponse.from(idea);
    }
}
