package io.openpulsechecker.schedulerlock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({SchedulerLockService.class, SchedulerLockServiceTest.TestConfig.class})
class SchedulerLockServiceTest {

    @Autowired
    private SchedulerLockService schedulerLockService;

    @Autowired
    private MutableClock mutableClock;

    @Test
    void lockAcquireRenewReleaseAndExpirySteal() {
        assertTrue(schedulerLockService.acquire("m1", "owner-a", Duration.ofSeconds(10)));
        mutableClock.advanceSeconds(1);
        assertFalse(schedulerLockService.renew("m1", "owner-b", Duration.ofSeconds(10)));

        assertTrue(schedulerLockService.renew("m1", "owner-a", Duration.ofSeconds(10)));

        mutableClock.advanceSeconds(11);
        assertTrue(schedulerLockService.acquire("m1", "owner-b", Duration.ofSeconds(5)));

        schedulerLockService.release("m1", "owner-b");
        assertTrue(schedulerLockService.acquire("m1", "owner-a", Duration.ofSeconds(5)));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-02-26T22:00:00Z"));
        }

        @Bean
        Clock clock(MutableClock mutableClock) {
            return mutableClock;
        }
    }

    static class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advanceSeconds(long s) { now = now.plusSeconds(s); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
