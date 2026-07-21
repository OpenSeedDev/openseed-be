package com.seedrank.feedback.create;

import com.seedrank.feedback.Feedback;

import jakarta.validation.constraints.NotNull;

record FeedbackCreateRequest(
        @NotNull Feedback.Type type,
        @NotNull String content,
        String evidenceUrl,
        String evidenceDescription) {
}
