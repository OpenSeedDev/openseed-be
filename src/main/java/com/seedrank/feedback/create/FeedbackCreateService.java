package com.seedrank.feedback.create;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.feedback.Feedback;
import com.seedrank.feedback.FeedbackRepository;
import com.seedrank.idea.Idea;
import com.seedrank.idea.IdeaRepository;
import com.seedrank.idea.IdeaStatus;
import com.seedrank.idea.draft.IdeaDraftNotFoundException;
import com.seedrank.member.UserRepository;
import com.seedrank.point.ActivityReward;
import com.seedrank.point.PointRewardService;

@Service
class FeedbackCreateService {
    private static final int DAILY_REWARD_LIMIT = 5;

    private final AccessTokenAuthenticator authenticator;
    private final IdeaRepository ideas;
    private final UserRepository users;
    private final FeedbackRepository feedbacks;
    private final PointRewardService rewards;
    private final Clock clock;

    FeedbackCreateService(AccessTokenAuthenticator authenticator, IdeaRepository ideas, UserRepository users,
            FeedbackRepository feedbacks, PointRewardService rewards, Clock clock) {
        this.authenticator = authenticator;
        this.ideas = ideas;
        this.users = users;
        this.feedbacks = feedbacks;
        this.rewards = rewards;
        this.clock = clock;
    }

    @Transactional
    FeedbackCreateResponse create(String authorization, UUID ideaId, FeedbackCreateRequest request) {
        UUID userId = authenticator.authenticate(authorization).userId();
        Idea idea = ideas.findById(ideaId).orElseThrow(IdeaDraftNotFoundException::new);
        if (idea.status() != IdeaStatus.PUBLISHED) {
            throw new IdeaDraftNotFoundException();
        }
        var author = users.findById(userId).orElseThrow(() -> new IllegalStateException("User not found"));
        Feedback feedback = feedbacks.save(Feedback.create(
                idea, author, request.type(), request.content(), request.evidenceUrl(),
                request.evidenceDescription(), clock.instant()));
        var reward = rewards.grant(userId, ActivityReward.FEEDBACK_CREATED, feedback.id(), DAILY_REWARD_LIMIT);
        return FeedbackCreateResponse.from(feedback, reward);
    }
}
