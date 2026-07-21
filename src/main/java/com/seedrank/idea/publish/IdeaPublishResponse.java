package com.seedrank.idea.publish;

import java.time.Instant;
import java.util.UUID;

import com.seedrank.idea.Idea;
import com.seedrank.idea.IdeaStatus;
import com.seedrank.idea.IdeaVisibility;
import com.seedrank.point.PointRewardResult;

public record IdeaPublishResponse(
        UUID id,
        IdeaStatus status,
        IdeaVisibility visibility,
        int currentUnitPrice,
        Instant publishedAt,
        RewardResponse reward) {

    static IdeaPublishResponse from(Idea idea, PointRewardResult reward) {
        return new IdeaPublishResponse(idea.id(), idea.status(), idea.visibility(), idea.currentUnitPrice(),
                idea.publishedAt(), new RewardResponse(reward.originalAmount(), reward.paidAmount(), reward.expiredAmount()));
    }

    static IdeaPublishResponse withoutReward(Idea idea) {
        return new IdeaPublishResponse(idea.id(), idea.status(), idea.visibility(), idea.currentUnitPrice(),
                idea.publishedAt(), new RewardResponse(0, 0, 0));
    }

    public record RewardResponse(int originalAmount, int paidAmount, int expiredAmount) {}
}
