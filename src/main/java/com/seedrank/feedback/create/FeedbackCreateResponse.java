package com.seedrank.feedback.create;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.seedrank.feedback.Feedback;
import com.seedrank.point.PointRewardResult;

@JsonInclude(JsonInclude.Include.NON_NULL)
record FeedbackCreateResponse(
        UUID id,
        UUID ideaId,
        UUID authorId,
        Feedback.Type type,
        String content,
        String evidenceUrl,
        String evidenceDescription,
        Instant createdAt,
        RewardResponse reward) {

    static FeedbackCreateResponse from(Feedback feedback, PointRewardResult reward) {
        return new FeedbackCreateResponse(
                feedback.id(), feedback.ideaId(), feedback.authorId(), feedback.type(), feedback.content(),
                feedback.evidenceUrl(), feedback.evidenceDescription(), feedback.createdAt(),
                new RewardResponse(reward.originalAmount(), reward.paidAmount(), reward.expiredAmount()));
    }

    record RewardResponse(int originalAmount, int paidAmount, int expiredAmount) {
    }
}
