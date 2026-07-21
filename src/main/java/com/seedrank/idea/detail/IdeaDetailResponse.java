package com.seedrank.idea.detail;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.seedrank.idea.Idea;
import com.seedrank.idea.IdeaStatus;
import com.seedrank.idea.IdeaVisibility;
import com.seedrank.idea.question.ValidationQuestion;
import com.seedrank.idea.question.ValidationQuestionResponse;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IdeaDetailResponse(
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
        IdeaVisibility visibility,
        Integer currentUnitPrice,
        long likeCount,
        boolean liked,
        long viewCount,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt) {

    static IdeaDetailResponse full(
            Idea idea,
            List<ValidationQuestion> questions,
            long likeCount,
            boolean liked,
            long viewCount) {
        return new IdeaDetailResponse(
                idea.id(), idea.status(), idea.title(), idea.category(), idea.summary(), idea.problem(),
                idea.targetCustomer(), idea.solution(), idea.businessModel(),
                questions.stream().map(ValidationQuestionResponse::from).toList(),
                idea.visibility(), idea.currentUnitPrice(), likeCount, liked, viewCount,
                idea.publishedAt(), idea.createdAt(), idea.updatedAt());
    }

    static IdeaDetailResponse semiPublicGuest(
            Idea idea,
            long likeCount,
            boolean liked,
            long viewCount) {
        return new IdeaDetailResponse(
                idea.id(), idea.status(), idea.title(), idea.category(), idea.summary(), idea.problem(),
                null, null, null, null,
                idea.visibility(), idea.currentUnitPrice(), likeCount, liked, viewCount,
                idea.publishedAt(), idea.createdAt(), idea.updatedAt());
    }

    static IdeaDetailResponse summaryOnly(
            Idea idea,
            long likeCount,
            boolean liked,
            long viewCount) {
        return new IdeaDetailResponse(
                idea.id(), idea.status(), idea.title(), idea.category(), idea.summary(),
                null, null, null, null, null,
                idea.visibility(), idea.currentUnitPrice(), likeCount, liked, viewCount,
                idea.publishedAt(), idea.createdAt(), idea.updatedAt());
    }
}
