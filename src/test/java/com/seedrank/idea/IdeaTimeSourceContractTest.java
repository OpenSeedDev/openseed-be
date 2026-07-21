package com.seedrank.idea;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class IdeaTimeSourceContractTest {

    @Test
    void applicationCallersCannotSupplyAnArbitraryDraftTimestamp() {
        boolean exposesTimestampBearingFactory = Arrays.stream(Idea.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> Modifier.isStatic(method.getModifiers()))
                .anyMatch(method -> Arrays.asList(method.getParameterTypes()).contains(Instant.class));

        assertThat(exposesTimestampBearingFactory).isFalse();
    }

    @Test
    void draftFactoryReadsTheInjectedClockOnceForBothTimestamps() {
        Instant expected = Instant.parse("2026-07-21T03:00:00Z");
        CountingClock clock = new CountingClock(expected);
        IdeaDraftFactory factory = new IdeaDraftFactory(clock);

        Idea idea = factory.create(
                UUID.randomUUID(),
                "제목",
                "ETC",
                null,
                "문제",
                null,
                null,
                null);

        assertThat(idea.createdAt()).isEqualTo(expected);
        assertThat(idea.updatedAt()).isEqualTo(expected);
        assertThat(clock.readCount()).isOne();
    }

    private static final class CountingClock extends Clock {
        private final Instant firstValue;
        private final AtomicInteger reads = new AtomicInteger();

        private CountingClock(Instant firstValue) {
            this.firstValue = firstValue;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return firstValue.plusSeconds(reads.getAndIncrement());
        }

        private int readCount() {
            return reads.get();
        }
    }
}
