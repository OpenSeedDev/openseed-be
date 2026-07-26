package com.seedrank.feedback.top;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TopContributorService {
    private static final Duration RECENT_PERIOD = Duration.ofDays(30);

    private final TopContributorQuery query;
    private final Clock clock;

    TopContributorService(TopContributorQuery query, Clock clock) {
        this.query = query;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    List<TopContributorResponse> get() {
        Instant recentThreshold = clock.instant().minus(RECENT_PERIOD);
        return query.findTopFive(recentThreshold);
    }
}
