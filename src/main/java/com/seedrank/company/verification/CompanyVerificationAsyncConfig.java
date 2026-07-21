package com.seedrank.company.verification;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
class CompanyVerificationAsyncConfig {
    static final String EXECUTOR = "companyVerificationExecutor";

    @Bean(name = EXECUTOR)
    Executor companyVerificationExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("company-verification-");
        executor.initialize();
        return executor;
    }
}
