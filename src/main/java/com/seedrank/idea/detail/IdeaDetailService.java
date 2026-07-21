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
import com.seedrank.idea.like.IdeaLikeQuery;
import com.seedrank.idea.question.ValidationQuestion;
import com.seedrank.idea.question.ValidationQuestionRepository;

@Service
class IdeaDetailService {
    private final AccessTokenAuthenticator authenticator;
    private final IdeaRepository ideas;
    private final ValidationQuestionRepository questions;
    private final IdeaLikeQuery likes;

    IdeaDetailService(
            AccessTokenAuthenticator authenticator,
            IdeaRepository ideas,
            ValidationQuestionRepository questions,
            IdeaLikeQuery likes) {
        this.authenticator = authenticator;
        this.ideas = ideas;
        this.questions = questions;
        this.likes = likes;
    }

    @Transactional(readOnly = true)
    IdeaDetailResponse get(String authorization, UUID ideaId) {
        UUID viewerId = authorization == null ? null : authenticator.authenticate(authorization).userId();
        Idea idea = ideas.findById(ideaId).orElseThrow(IdeaDraftNotFoundException::new);
        boolean author = idea.authorId().equals(viewerId);
        long likeCount = likes.count(ideaId);
        boolean liked = viewerId != null && likes.likedBy(ideaId, viewerId);

        if (idea.status() != IdeaStatus.PUBLISHED) {
            if (!author) {
                throw new IdeaDraftNotFoundException();
            }
            return IdeaDetailResponse.full(idea, questions(idea), likeCount, liked);
        }
        if (author || idea.visibility() == IdeaVisibility.PUBLIC) {
            return IdeaDetailResponse.full(idea, questions(idea), likeCount, liked);
        }
        if (idea.visibility() == IdeaVisibility.SEMI_PUBLIC) {
            return viewerId == null
                    ? IdeaDetailResponse.semiPublicGuest(idea, likeCount, liked)
                    : IdeaDetailResponse.full(idea, questions(idea), likeCount, liked);
        }
        return IdeaDetailResponse.summaryOnly(idea, likeCount, liked);
    }

    private List<ValidationQuestion> questions(Idea idea) {
        return questions.findByIdeaIdOrderBySortOrder(idea.id());
    }
}
