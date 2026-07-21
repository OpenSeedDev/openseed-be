package com.seedrank.feedback.manage;

import com.seedrank.feedback.Feedback;

import jakarta.validation.constraints.NotNull;

record FeedbackUpdateRequest(
        @NotNull Feedback.Type type,
        @NotNull String content,
        String evidenceUrl,
        String evidenceDescription) {
}
