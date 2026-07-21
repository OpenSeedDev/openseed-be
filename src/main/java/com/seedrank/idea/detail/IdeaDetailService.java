package com.seedrank.idea.detail;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.idea.Idea;
import com.seedrank.idea.IdeaRepository;
import com.seedrank.idea.IdeaStatus;
import com.seedrank.idea.IdeaVisibility;
import com.seedrank.idea.draft.IdeaDraftNotFoundException;
import com.seedrank.idea.question.ValidationQuestion;
import com.seedrank.idea.question.ValidationQuestionRepository;

@Service
class IdeaDetailService {
    private final AccessTokenAuthenticator authenticator;
    private final IdeaRepository ideas;
    private final ValidationQuestionRepository questions;

    IdeaDetailService(
            AccessTokenAuthenticator authenticator,
            IdeaRepository ideas,
            ValidationQuestionRepository questions) {
        this.authenticator = authenticator;
        this.ideas = ideas;
        this.questions = questions;
    }

    @Transactional(readOnly = true)
    IdeaDetailResponse get(String authorization, UUID ideaId) {
        UUID viewerId = authorization == null ? null : authenticator.authenticate(authorization).userId();
        Idea idea = ideas.findById(ideaId).orElseThrow(IdeaDraftNotFoundException::new);
        boolean author = idea.authorId().equals(viewerId);

        if (idea.status() != IdeaStatus.PUBLISHED) {
            if (!author) {
                throw new IdeaDraftNotFoundException();
            }
            return IdeaDetailResponse.full(idea, questions(idea));
        }
        if (author || idea.visibility() == IdeaVisibility.PUBLIC) {
            return IdeaDetailResponse.full(idea, questions(idea));
        }
        if (idea.visibility() == IdeaVisibility.SEMI_PUBLIC) {
            return viewerId == null
                    ? IdeaDetailResponse.semiPublicGuest(idea)
                    : IdeaDetailResponse.full(idea, questions(idea));
        }
        return IdeaDetailResponse.summaryOnly(idea);
    }

    private List<ValidationQuestion> questions(Idea idea) {
        return questions.findByIdeaIdOrderBySortOrder(idea.id());
    }
}
