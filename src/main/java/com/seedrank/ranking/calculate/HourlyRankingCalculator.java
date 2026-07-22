package com.seedrank.ranking.calculate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class HourlyRankingCalculator {
    private static final int DECAY_GRACE_DAYS = 7;
    private static final double DAILY_DECAY_RATE = 0.02;

    private static final Comparator<UnrankedResult> ORDER = Comparator
            .comparingDouble((UnrankedResult result) -> result.score().totalScore()).reversed()
            .thenComparing(Comparator.comparingInt(
                    (UnrankedResult result) -> result.signal().uniqueActiveInvestors()).reversed())
            .thenComparing(Comparator.comparingInt(
                    (UnrankedResult result) -> result.signal().acceptedFeedbackCount()).reversed())
            .thenComparing(Comparator.comparingInt(
                    (UnrankedResult result) -> result.signal().companyInterestCount()).reversed())
            .thenComparing(result -> result.signal().publishedAt())
            .thenComparing(result -> result.signal().ideaId());

    public List<RankingResult> calculate(Collection<RankingSignal> signals, Instant targetHour) {
        Objects.requireNonNull(signals, "signals must not be null");
        Objects.requireNonNull(targetHour, "targetHour must not be null");
        if (!targetHour.equals(targetHour.truncatedTo(ChronoUnit.HOURS))) {
            throw new IllegalArgumentException("targetHour must be aligned to a UTC hour");
        }

        Set<java.util.UUID> seenIdeaIds = new HashSet<>();
        List<UnrankedResult> unranked = new ArrayList<>(signals.size());
        for (RankingSignal signal : signals) {
            Objects.requireNonNull(signal, "signal must not be null");
            if (!seenIdeaIds.add(signal.ideaId())) {
                throw new IllegalArgumentException("duplicate idea signal: " + signal.ideaId());
            }
            if (signal.publishedAt().isAfter(targetHour)) {
                throw new IllegalArgumentException("publishedAt must not be after targetHour");
            }
            unranked.add(new UnrankedResult(signal, score(signal, targetHour)));
        }
        unranked.sort(ORDER);

        List<RankingResult> ranked = new ArrayList<>(unranked.size());
        for (int index = 0; index < unranked.size(); index++) {
            UnrankedResult result = unranked.get(index);
            RankingSignal signal = result.signal();
            ranked.add(new RankingResult(signal.ideaId(), index + 1, result.score(),
                    signal.uniqueActiveInvestors(), signal.acceptedFeedbackCount(),
                    signal.companyInterestCount(), signal.likeCount(), signal.publishedAt(), targetHour));
        }
        return List.copyOf(ranked);
    }

    private RankingScore score(RankingSignal signal, Instant targetHour) {
        double investment = Math.log(signal.activePrincipal() + 1.0) * 35.0;
        double diversity = Math.min(signal.uniqueActiveInvestors() * 3.0, 20.0);
        double company = Math.min(signal.companyInterestCount() * 12.0, 36.0);
        double feedback = Math.min(signal.feedbackCount() * 2.0
                + signal.acceptedFeedbackCount() * 5.0, 30.0);
        double reaction = Math.min(signal.likeCount() * 0.8
                + Math.log(signal.viewCount() + 1.0) * 2.0, 20.0);
        double growth = Math.min(signal.normalized24hGrowth(), 15.0);
        double subtotal = investment + diversity + company + feedback + reaction + growth;
        long ageDays = Duration.between(signal.publishedAt(), targetHour).toDays();
        long decayDays = Math.max(0, ageDays - DECAY_GRACE_DAYS);
        double decayRate = Math.min(decayDays * DAILY_DECAY_RATE, 1.0);
        double decay = subtotal * decayRate;
        double total = subtotal - decay;
        return new RankingScore(investment, diversity, company, feedback, reaction, growth,
                decay, subtotal, total);
    }

    private record UnrankedResult(RankingSignal signal, RankingScore score) {
    }
}
