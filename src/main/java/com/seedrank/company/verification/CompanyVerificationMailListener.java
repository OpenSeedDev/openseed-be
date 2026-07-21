package com.seedrank.company.verification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class CompanyVerificationMailListener {
    private static final Logger log = LoggerFactory.getLogger(CompanyVerificationMailListener.class);
    private final CompanyVerificationMailSender sender;

    CompanyVerificationMailListener(CompanyVerificationMailSender sender) {
        this.sender = sender;
    }

    @Async(CompanyVerificationAsyncConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(CompanyVerificationRequested event) {
        try {
            sender.send(event.companyEmail(), event.rawToken(), event.expiresAt());
        } catch (RuntimeException exception) {
            log.error("Company verification mail delivery failed");
        }
    }
}
