package com.seedrank.ranking.publish;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.ranking.calculate.HourlyRankingCalculator;
import com.seedrank.ranking.calculate.RankingResult;
import com.seedrank.ranking.calculate.RankingSignal;

@Service
public class HourlyRankingPublicationService {
    private final RankingSignalQuery signalQuery;
    private final RankingCurrentStore currentStore;
    private final HourlyRankingCalculator calculator = new HourlyRankingCalculator();

    HourlyRankingPublicationService(RankingSignalQuery signalQuery, RankingCurrentStore currentStore) {
        this.signalQuery = signalQuery;
        this.currentStore = currentStore;
    }

    @Transactional
    public RankingPublicationOutcome publish(Instant targetHour) {
        if (targetHour == null || !targetHour.equals(targetHour.truncatedTo(ChronoUnit.HOURS))) {
            throw new IllegalArgumentException("targetHour must be aligned to a UTC hour");
        }
        if (!currentStore.claim(targetHour)) {
            return new RankingPublicationOutcome(false, targetHour, currentStore.currentCount());
        }
        List<RankingSignal> signals = signalQuery.snapshot(targetHour);
        List<RankingResult> results = calculator.calculate(signals, targetHour);
        currentStore.publishClaimed(targetHour, results);
        return new RankingPublicationOutcome(true, targetHour, results.size());
    }
}
