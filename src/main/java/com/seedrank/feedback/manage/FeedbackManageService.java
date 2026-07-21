package com.seedrank.feedback.manage;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.feedback.Feedback;
import com.seedrank.feedback.FeedbackRepository;
import com.seedrank.feedback.FeedbackRevision;
import com.seedrank.feedback.FeedbackRevision.RevisionType;
import com.seedrank.feedback.FeedbackRevisionRepository;

@Service
class FeedbackManageService {
    private final AccessTokenAuthenticator authenticator;
    private final FeedbackRepository feedbacks;
    private final FeedbackRevisionRepository revisions;
    private final Clock clock;

    FeedbackManageService(
            AccessTokenAuthenticator authenticator,
            FeedbackRepository feedbacks,
            FeedbackRevisionRepository revisions,
            Clock clock) {
        this.authenticator = authenticator;
        this.feedbacks = feedbacks;
        this.revisions = revisions;
        this.clock = clock;
    }

    @Transactional
    FeedbackUpdateResponse update(String authorization, UUID feedbackId, FeedbackUpdateRequest request) {
        UUID userId = authenticator.authenticate(authorization).userId();
        Feedback feedback = ownedActiveFeedback(feedbackId, userId);
        Instant now = clock.instant();
        revisions.save(FeedbackRevision.snapshot(feedback, RevisionType.EDITED, now));
        feedback.update(request.type(), request.content(), request.evidenceUrl(), request.evidenceDescription(), now);
        return FeedbackUpdateResponse.from(feedback);
    }

    @Transactional
    void delete(String authorization, UUID feedbackId) {
        UUID userId = authenticator.authenticate(authorization).userId();
        Feedback feedback = ownedActiveFeedback(feedbackId, userId);
        Instant now = clock.instant();
        revisions.save(FeedbackRevision.snapshot(feedback, RevisionType.DELETED, now));
        feedback.delete(now);
    }

    private Feedback ownedActiveFeedback(UUID feedbackId, UUID userId) {
        Feedback feedback = feedbacks.findByIdForUpdate(feedbackId).orElseThrow(FeedbackNotFoundException::new);
        if (!feedback.authorId().equals(userId) || feedback.deletedAt() != null) {
            throw new FeedbackNotFoundException();
        }
        return feedback;
    }
}
