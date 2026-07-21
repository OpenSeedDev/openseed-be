package com.seedrank.idea.question;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.idea.IdeaRepository;
import com.seedrank.idea.draft.IdeaDraftNotFoundException;

@Service
class ValidationQuestionService {
    private final AccessTokenAuthenticator authenticator;
    private final IdeaRepository ideas;
    private final ValidationQuestionRepository questions;

    ValidationQuestionService(
            AccessTokenAuthenticator authenticator,
            IdeaRepository ideas,
            ValidationQuestionRepository questions) {
        this.authenticator = authenticator;
        this.ideas = ideas;
        this.questions = questions;
    }

    @Transactional
    ValidationQuestionsResponse replace(
            String authorization,
            UUID ideaId,
            ValidationQuestionRequest request) {
        UUID authorId = authenticator.authenticate(authorization).userId();
        ideas.findByIdAndAuthorId(ideaId, authorId).orElseThrow(IdeaDraftNotFoundException::new);

        questions.deleteAllByIdeaId(ideaId);
        List<ValidationQuestion> replacements = IntStream.range(0, request.questions().size())
                .mapToObj(index -> ValidationQuestion.create(ideaId, request.questions().get(index), index + 1))
                .toList();
        List<ValidationQuestionResponse> saved = questions.saveAll(replacements).stream()
                .map(ValidationQuestionResponse::from)
                .toList();
        return new ValidationQuestionsResponse(saved);
    }
}
