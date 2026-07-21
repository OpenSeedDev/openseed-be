package com.seedrank.feedback.list;

import java.time.Instant;
import java.util.UUID;

import com.seedrank.feedback.Feedback;

record FeedbackItemResponse(
        UUID id,
        Feedback.Type type,
        String content,
        String evidenceUrl,
        String evidenceDescription,
        String authorProfileId,
        boolean accepted,
        Instant acceptedAt,
        Instant editedAt,
        Instant createdAt) {

    static FeedbackItemResponse from(Feedback feedback) {
        return new FeedbackItemResponse(
                feedback.id(), feedback.type(), feedback.content(), feedback.evidenceUrl(),
                feedback.evidenceDescription(), feedback.authorProfileId(), feedback.acceptedAt() != null,
                feedback.acceptedAt(), feedback.editedAt(), feedback.createdAt());
    }
}
