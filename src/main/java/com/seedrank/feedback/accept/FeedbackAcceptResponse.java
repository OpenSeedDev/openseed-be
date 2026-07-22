package com.seedrank.feedback.accept;

import java.time.Instant;
import java.util.UUID;

import com.seedrank.feedback.Contribution;
import com.seedrank.feedback.Feedback;
import com.seedrank.point.PointRewardResult;

record FeedbackAcceptResponse(
        UUID feedbackId,
        UUID ideaId,
        UUID contributionId,
        UUID contributorId,
        Instant acceptedAt,
        RewardResponse reward) {

    static FeedbackAcceptResponse from(
            Feedback feedback, Contribution contribution, PointRewardResult reward) {
        return new FeedbackAcceptResponse(
                feedback.id(), feedback.ideaId(), contribution.id(), contribution.userId(), feedback.acceptedAt(),
                new RewardResponse(reward.originalAmount(), reward.paidAmount(), reward.expiredAmount()));
    }

    record RewardResponse(int originalAmount, int paidAmount, int expiredAmount) {}
}
