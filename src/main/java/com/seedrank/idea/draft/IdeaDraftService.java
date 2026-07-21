package com.seedrank.idea.draft;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.idea.Idea;
import com.seedrank.idea.IdeaDraftFactory;
import com.seedrank.idea.IdeaRepository;
import com.seedrank.idea.question.ValidationQuestionRepository;

@Service
class IdeaDraftService {
    private final AccessTokenAuthenticator authenticator;
    private final IdeaRepository ideas;
    private final IdeaDraftFactory ideaDraftFactory;
    private final ValidationQuestionRepository questions;

    IdeaDraftService(
            AccessTokenAuthenticator authenticator,
            IdeaRepository ideas,
            IdeaDraftFactory ideaDraftFactory,
            ValidationQuestionRepository questions) {
        this.authenticator = authenticator;
        this.ideas = ideas;
        this.ideaDraftFactory = ideaDraftFactory;
        this.questions = questions;
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

    @Transactional(readOnly = true)
    IdeaDraftResponse get(String authorization, UUID ideaId) {
        UUID authorId = authenticator.authenticate(authorization).userId();
        return ideas.findByIdAndAuthorId(ideaId, authorId)
                .map(idea -> IdeaDraftResponse.from(idea, questions.findByIdeaIdOrderBySortOrder(idea.id())))
                .orElseThrow(IdeaDraftNotFoundException::new);
    }
}
