package com.seedrank.idea.draft;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.seedrank.idea.Idea;
import com.seedrank.idea.IdeaStatus;
import com.seedrank.idea.question.ValidationQuestion;
import com.seedrank.idea.question.ValidationQuestionResponse;

public record IdeaDraftResponse(
        UUID id,
        IdeaStatus status,
        String title,
        String category,
        String summary,
        String problem,
        String targetCustomer,
        String solution,
        String businessModel,
        List<ValidationQuestionResponse> validationQuestions,
        Instant createdAt,
        Instant updatedAt) {

    public static IdeaDraftResponse from(Idea idea, List<ValidationQuestion> questions) {
        return new IdeaDraftResponse(
                idea.id(), idea.status(), idea.title(), idea.category(), idea.summary(), idea.problem(),
                idea.targetCustomer(), idea.solution(), idea.businessModel(),
                questions.stream().map(ValidationQuestionResponse::from).toList(),
                idea.createdAt(), idea.updatedAt());
    }
}
