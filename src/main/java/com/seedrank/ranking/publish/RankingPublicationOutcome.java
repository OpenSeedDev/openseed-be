package com.seedrank.ranking.publish;

import java.time.Instant;

public record RankingPublicationOutcome(boolean published, Instant targetHour, int ideaCount) {
}
