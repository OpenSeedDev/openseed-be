package com.seedrank.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class TimeConfigTest {

    @Test
    void providesAClockUsingAsiaSeoul() {
        try (var context = new AnnotationConfigApplicationContext(TimeConfig.class)) {
            Clock clock = context.getBean(Clock.class);

            assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
        }
    }
}
