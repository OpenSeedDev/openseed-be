package com.seedrank.unit.consistency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.financial-consistency.enabled",
        havingValue = "true",
        matchIfMissing = true)
class FinancialConsistencyScheduler {
    private static final Logger log = LoggerFactory.getLogger(FinancialConsistencyScheduler.class);

    private final FinancialConsistencyCheckService service;

    FinancialConsistencyScheduler(FinancialConsistencyCheckService service) {
        this.service = service;
    }

    @Scheduled(cron = "${app.financial-consistency.cron:0 15 * * * *}", zone = "UTC")
    void check() {
        service.run().ifPresent(result -> log.info(
                "financial_consistency_check_completed checkId={} status={} findingCount={}",
                result.checkId(), result.status(), result.findingCount()));
    }
}
