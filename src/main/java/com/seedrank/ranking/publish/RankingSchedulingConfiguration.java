package com.seedrank.ranking.publish;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.ranking.scheduling-enabled", havingValue = "true", matchIfMissing = true)
class RankingSchedulingConfiguration {
}
