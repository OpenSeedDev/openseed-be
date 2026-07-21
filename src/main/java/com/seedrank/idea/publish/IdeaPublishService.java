package com.seedrank.idea.publish;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.idea.Idea;
import com.seedrank.idea.IdeaRepository;
import com.seedrank.idea.IdeaStatus;
import com.seedrank.idea.IdeaVisibility;
import com.seedrank.idea.draft.IdeaDraftNotFoundException;
import com.seedrank.idea.question.ValidationQuestionRepository;
import com.seedrank.point.ActivityReward;
import com.seedrank.point.PointRewardService;

@Service
class IdeaPublishService {
    private static final int DAILY_PUBLISH_REWARD_LIMIT = 2;

    private final AccessTokenAuthenticator authenticator;
    private final IdeaRepository ideas;
    private final ValidationQuestionRepository questions;
    private final IdeaVersionRepository versions;
    private final IdeaTimelineEventRepository timeline;
    private final PointRewardService rewards;
    private final Clock clock;

    IdeaPublishService(AccessTokenAuthenticator authenticator, IdeaRepository ideas,
            ValidationQuestionRepository questions, IdeaVersionRepository versions,
            IdeaTimelineEventRepository timeline, PointRewardService rewards, Clock clock) {
        this.authenticator = authenticator;
        this.ideas = ideas;
        this.questions = questions;
        this.versions = versions;
        this.timeline = timeline;
        this.rewards = rewards;
        this.clock = clock;
    }

    @Transactional
    IdeaPublishResponse publish(String authorization, UUID ideaId, IdeaPublishRequest request) {
        UUID authorId = authenticator.authenticate(authorization).userId();
        Idea idea = ideas.findByIdAndAuthorIdForUpdate(ideaId, authorId)
                .orElseThrow(IdeaDraftNotFoundException::new);
        if (idea.status() != IdeaStatus.DRAFT) {
            throw new IdeaAlreadyPublishedException();
        }
        var questionRows = questions.findByIdeaIdOrderBySortOrder(ideaId);
        if (questionRows.isEmpty() || questionRows.size() > 3) {
            throw new IdeaNotReadyToPublishException();
        }

        Instant now = clock.instant();
        try {
            idea.publish(request.visibility(), now);
        } catch (IllegalArgumentException exception) {
            throw new IdeaNotReadyToPublishException();
        }
        versions.save(IdeaVersion.first(idea, questionRows.stream().map(row -> row.question()).toList(), now));
        timeline.save(IdeaTimelineEvent.published(ideaId, authorId, now));

        if (request.visibility() == IdeaVisibility.MATCHING) {
            return IdeaPublishResponse.withoutReward(idea);
        }
        var reward = rewards.grant(authorId, ActivityReward.IDEA_PUBLISHED, ideaId, DAILY_PUBLISH_REWARD_LIMIT);
        return IdeaPublishResponse.from(idea, reward);
    }
}
