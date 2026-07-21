package com.seedrank.idea.update;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.idea.Idea;
import com.seedrank.idea.IdeaRepository;
import com.seedrank.idea.IdeaStatus;
import com.seedrank.idea.draft.IdeaDraftNotFoundException;
import com.seedrank.idea.publish.IdeaVersionAppender;
import com.seedrank.idea.question.ValidationQuestionRepository;

@Service
class IdeaUpdateService {
    private final AccessTokenAuthenticator authenticator;
    private final IdeaRepository ideas;
    private final ValidationQuestionRepository questions;
    private final IdeaVersionAppender versions;
    private final Clock clock;

    IdeaUpdateService(
            AccessTokenAuthenticator authenticator,
            IdeaRepository ideas,
            ValidationQuestionRepository questions,
            IdeaVersionAppender versions,
            Clock clock) {
        this.authenticator = authenticator;
        this.ideas = ideas;
        this.questions = questions;
        this.versions = versions;
        this.clock = clock;
    }

    @Transactional
    IdeaUpdateResponse update(String authorization, UUID ideaId, IdeaUpdateRequest request) {
        UUID authorId = authenticator.authenticate(authorization).userId();
        Idea idea = ideas.findByIdAndAuthorIdForUpdate(ideaId, authorId)
                .orElseThrow(IdeaDraftNotFoundException::new);
        if (idea.status() != IdeaStatus.PUBLISHED) {
            throw new IdeaNotPublishedException();
        }

        Instant now = clock.instant();
        idea.updatePublishedContent(
                request.title(), request.category(), request.summary(), request.problem(), request.targetCustomer(),
                request.solution(), request.businessModel(), now);
        var questionSnapshot = questions.findByIdeaIdOrderBySortOrder(ideaId).stream()
                .map(question -> question.question())
                .toList();
        versions.append(idea, questionSnapshot, now);
        return IdeaUpdateResponse.from(idea);
    }
}
