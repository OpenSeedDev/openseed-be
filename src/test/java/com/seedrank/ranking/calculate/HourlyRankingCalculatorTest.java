package com.seedrank.ranking.calculate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class HourlyRankingCalculatorTest {
    private static final Instant TARGET_HOUR = Instant.parse("2026-07-22T03:00:00Z");

    private final HourlyRankingCalculator calculator = new HourlyRankingCalculator();

    @Test
    void calculatesApprovedWeightedComponentsAndCaps() {
        RankingSignal signal = signal(
                "00000000-0000-0000-0000-000000000001",
                99, 9, 4, 20, 4, 100, 999_999, 18, TARGET_HOUR.minusSeconds(3 * 86_400));

        RankingScore score = calculator.calculate(List.of(signal), TARGET_HOUR).getFirst().score();

        assertThat(score.investmentScore()).isCloseTo(Math.log(100) * 35, within());
        assertThat(score.diversityScore()).isEqualTo(20);
        assertThat(score.companyScore()).isEqualTo(36);
        assertThat(score.feedbackScore()).isEqualTo(30);
        assertThat(score.reactionScore()).isEqualTo(20);
        assertThat(score.growthScore()).isEqualTo(15);
        assertThat(score.decayScore()).isZero();
        assertThat(score.totalScore()).isCloseTo(Math.log(100) * 35 + 121, within());
    }

    @Test
    void appliesNoDecayThroughSevenDaysAndTwoPercentOfBaseScorePerDayAfterward() {
        RankingSignal sevenDays = signal("00000000-0000-0000-0000-000000000001",
                99, 0, 0, 0, 0, 0, 0, 0, TARGET_HOUR.minusSeconds(7 * 86_400));
        RankingSignal eightDays = signal("00000000-0000-0000-0000-000000000002",
                99, 0, 0, 0, 0, 0, 0, 0, TARGET_HOUR.minusSeconds(8 * 86_400));
        RankingSignal nineDays = signal("00000000-0000-0000-0000-000000000003",
                99, 0, 0, 0, 0, 0, 0, 0, TARGET_HOUR.minusSeconds(9 * 86_400));

        List<RankingResult> results = calculator.calculate(List.of(nineDays, eightDays, sevenDays), TARGET_HOUR);
        RankingScore noDecay = result(results, sevenDays.ideaId()).score();
        RankingScore oneDayDecay = result(results, eightDays.ideaId()).score();
        RankingScore twoDayDecay = result(results, nineDays.ideaId()).score();

        assertThat(noDecay.decayScore()).isZero();
        assertThat(oneDayDecay.decayScore()).isCloseTo(oneDayDecay.subtotalScore() * 0.02, within());
        assertThat(oneDayDecay.totalScore()).isCloseTo(oneDayDecay.subtotalScore() * 0.98, within());
        assertThat(twoDayDecay.decayScore()).isCloseTo(twoDayDecay.subtotalScore() * 0.04, within());
    }

    @Test
    void resolvesTiesByDiversityAcceptedFeedbackCompanyInterestAndEarlierPublishTime() {
        RankingSignal diversity = signal("00000000-0000-0000-0000-000000000001",
                0, 4, 0, 0, 0, 0, 0, 15, TARGET_HOUR.minusSeconds(100));
        RankingSignal accepted = signal("00000000-0000-0000-0000-000000000002",
                0, 3, 0, 1, 1, 0, 0, 11, TARGET_HOUR.minusSeconds(100));
        RankingSignal company = signal("00000000-0000-0000-0000-000000000003",
                0, 3, 1, 0, 0, 0, 0, 6, TARGET_HOUR.minusSeconds(100));
        RankingSignal earlier = signal("00000000-0000-0000-0000-000000000004",
                0, 3, 0, 6, 0, 0, 0, 6, TARGET_HOUR.minusSeconds(200));
        RankingSignal later = signal("00000000-0000-0000-0000-000000000005",
                0, 3, 0, 6, 0, 0, 0, 6, TARGET_HOUR.minusSeconds(100));

        List<RankingResult> results = calculator.calculate(
                List.of(later, company, accepted, earlier, diversity), TARGET_HOUR);

        assertThat(results).extracting(RankingResult::ideaId)
                .containsExactly(diversity.ideaId(), accepted.ideaId(), company.ideaId(),
                        earlier.ideaId(), later.ideaId());
        assertThat(results).extracting(RankingResult::position).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void usesIdeaIdAsFinalStableTieBreakerAndReturnsSameFixtureResult() {
        RankingSignal first = signal("00000000-0000-0000-0000-000000000001",
                0, 0, 0, 0, 0, 0, 0, 0, TARGET_HOUR.minusSeconds(1));
        RankingSignal second = signal("00000000-0000-0000-0000-000000000002",
                0, 0, 0, 0, 0, 0, 0, 0, TARGET_HOUR.minusSeconds(1));

        List<RankingResult> firstRun = calculator.calculate(List.of(second, first), TARGET_HOUR);
        List<RankingResult> secondRun = calculator.calculate(List.of(first, second), TARGET_HOUR);

        assertThat(firstRun).isEqualTo(secondRun);
        assertThat(firstRun).extracting(RankingResult::ideaId).containsExactly(first.ideaId(), second.ideaId());
    }

    @Test
    void rejectsInvalidSignalsDuplicateIdeasAndNonHourlyTarget() {
        assertThatIllegalArgumentException().isThrownBy(() -> signal(
                "00000000-0000-0000-0000-000000000001", -1, 0, 0, 0, 0, 0, 0, 0, TARGET_HOUR));
        assertThatIllegalArgumentException().isThrownBy(() -> signal(
                "00000000-0000-0000-0000-000000000001", 0, 0, 0, 0, 0, 0, 0, Double.NaN, TARGET_HOUR));

        RankingSignal duplicate = signal("00000000-0000-0000-0000-000000000001",
                0, 0, 0, 0, 0, 0, 0, 0, TARGET_HOUR);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(List.of(duplicate, duplicate), TARGET_HOUR));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(List.of(duplicate), TARGET_HOUR.plusSeconds(1)));
    }

    private RankingSignal signal(String id, long principal, int investors, int companies,
            int feedbacks, int accepted, int likes, long views, double growth, Instant publishedAt) {
        return new RankingSignal(UUID.fromString(id), principal, investors, companies, feedbacks,
                accepted, likes, views, growth, publishedAt);
    }

    private RankingResult result(List<RankingResult> results, UUID ideaId) {
        return results.stream().filter(result -> result.ideaId().equals(ideaId)).findFirst().orElseThrow();
    }

    private org.assertj.core.data.Offset<Double> within() {
        return org.assertj.core.data.Offset.offset(1.0e-9);
    }
}
