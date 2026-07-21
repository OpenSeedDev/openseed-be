package com.seedrank.feedback.manage;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.seedrank.feedback.Feedback;

@JsonInclude(JsonInclude.Include.NON_NULL)
record FeedbackUpdateResponse(
        UUID id,
        Feedback.Type type,
        String content,
        String evidenceUrl,
        String evidenceDescription,
        Instant editedAt) {

    static FeedbackUpdateResponse from(Feedback feedback) {
        return new FeedbackUpdateResponse(
                feedback.id(), feedback.type(), feedback.content(), feedback.evidenceUrl(),
                feedback.evidenceDescription(), feedback.editedAt());
    }
}
