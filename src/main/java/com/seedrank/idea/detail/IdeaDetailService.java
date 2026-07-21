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
import com.seedrank.idea.metrics.IdeaViewMetricRecorder;
import com.seedrank.idea.question.ValidationQuestion;
import com.seedrank.idea.question.ValidationQuestionRepository;

@Service
class IdeaDetailService {
    private final AccessTokenAuthenticator authenticator;
    private final IdeaRepository ideas;
    private final ValidationQuestionRepository questions;
    private final IdeaViewMetricRecorder viewMetrics;

    IdeaDetailService(
            AccessTokenAuthenticator authenticator,
            IdeaRepository ideas,
            ValidationQuestionRepository questions,
            IdeaViewMetricRecorder viewMetrics) {
        this.authenticator = authenticator;
        this.ideas = ideas;
        this.questions = questions;
        this.viewMetrics = viewMetrics;
    }

    @Transactional
    IdeaDetailResponse get(String authorization, UUID ideaId, String guestSessionId) {
        var principal = authorization == null ? null : authenticator.authenticate(authorization);
        UUID viewerId = principal == null ? null : principal.userId();
        Idea idea = ideas.findById(ideaId).orElseThrow(IdeaDraftNotFoundException::new);
        boolean author = idea.authorId().equals(viewerId);

        if (idea.status() != IdeaStatus.PUBLISHED) {
            if (!author) {
                throw new IdeaDraftNotFoundException();
            }
            return IdeaDetailResponse.full(idea, questions(idea), 0L);
        }
        long viewCount = viewMetrics.record(ideaId, viewerId, guestSessionId);
        if (author || idea.visibility() == IdeaVisibility.PUBLIC) {
            return IdeaDetailResponse.full(idea, questions(idea), viewCount);
        }
        if (idea.visibility() == IdeaVisibility.SEMI_PUBLIC) {
            return viewerId == null
                    ? IdeaDetailResponse.semiPublicGuest(idea, viewCount)
                    : IdeaDetailResponse.full(idea, questions(idea), viewCount);
        }
        return IdeaDetailResponse.summaryOnly(idea, viewCount);
    }

    private List<ValidationQuestion> questions(Idea idea) {
        return questions.findByIdeaIdOrderBySortOrder(idea.id());
    }
}
