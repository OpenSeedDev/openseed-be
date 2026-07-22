package com.seedrank.feedback.accept;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.feedback.Contribution;
import com.seedrank.feedback.ContributionRepository;
import com.seedrank.feedback.Feedback;
import com.seedrank.feedback.FeedbackRepository;
import com.seedrank.feedback.manage.FeedbackNotFoundException;
import com.seedrank.idea.publish.IdeaTimelineEvent;
import com.seedrank.idea.publish.IdeaTimelineEventRepository;
import com.seedrank.point.ActivityReward;
import com.seedrank.point.PointRewardResult;
import com.seedrank.point.PointRewardService;

@Service
class FeedbackAcceptService {
    private final AccessTokenAuthenticator authenticator;
    private final FeedbackRepository feedbacks;
    private final ContributionRepository contributions;
    private final IdeaTimelineEventRepository timeline;
    private final PointRewardService rewards;
    private final Clock clock;

    FeedbackAcceptService(
            AccessTokenAuthenticator authenticator,
            FeedbackRepository feedbacks,
            ContributionRepository contributions,
            IdeaTimelineEventRepository timeline,
            PointRewardService rewards,
            Clock clock) {
        this.authenticator = authenticator;
        this.feedbacks = feedbacks;
        this.contributions = contributions;
        this.timeline = timeline;
        this.rewards = rewards;
        this.clock = clock;
    }

    @Transactional
    FeedbackAcceptResponse accept(String authorization, UUID feedbackId) {
        UUID requesterId = authenticator.authenticate(authorization).userId();
        Feedback feedback = feedbacks.findByIdForUpdate(feedbackId)
                .orElseThrow(FeedbackNotFoundException::new);
        if (feedback.deletedAt() != null || !feedback.ideaAuthorId().equals(requesterId)) {
            throw new FeedbackNotFoundException();
        }
        if (feedback.acceptedAt() != null) throw new FeedbackAlreadyAcceptedException();

        Instant now = clock.instant();
        feedback.accept(now);
        Contribution contribution = contributions.save(Contribution.from(feedback, now));
        timeline.save(IdeaTimelineEvent.feedbackAccepted(
                feedback.ideaId(), feedback.authorId(), feedback.id(), now));
        PointRewardResult reward = rewards.grantScoped(
                feedback.authorId(), ActivityReward.FEEDBACK_ACCEPTED, feedback.id(), feedback.ideaId(), 1);
        return FeedbackAcceptResponse.from(feedback, contribution, reward);
    }
}
