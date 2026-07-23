package com.seedrank.ranking.publish;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class HourlyRankingScheduler {
    private final HourlyRankingPublicationService service;
    private final Clock clock;

    HourlyRankingScheduler(HourlyRankingPublicationService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    void publishCurrentUtcHour() {
        Instant targetHour = clock.instant().truncatedTo(ChronoUnit.HOURS);
        service.publish(targetHour);
    }
}
