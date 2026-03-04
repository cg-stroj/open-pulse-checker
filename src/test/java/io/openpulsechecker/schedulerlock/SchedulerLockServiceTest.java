package io.openpulsechecker.schedulerlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import io.openpulsechecker.support.H2TestDatabaseSupport;

@DataJpaTest
@Import({SchedulerLockService.class, SchedulerLockServiceTest.TestConfig.class})
class SchedulerLockServiceTest extends H2TestDatabaseSupport {

    @Autowired
    private SchedulerLockService schedulerLockService;

    @Autowired
    private MutableClock mutableClock;

    @Test
    void lockAcquireRenewReleaseAndExpirySteal() {
        assertEquals(LockAcquireOutcome.ACQUIRED,
                schedulerLockService.acquire("m1", "owner-a", Duration.ofSeconds(10)));
        mutableClock.advanceSeconds(1);
        assertFalse(schedulerLockService.renew("m1", "owner-b", Duration.ofSeconds(10)));

        assertTrue(schedulerLockService.renew("m1", "owner-a", Duration.ofSeconds(10)));

        mutableClock.advanceSeconds(11);
        assertEquals(LockAcquireOutcome.STOLEN,
                schedulerLockService.acquire("m1", "owner-b", Duration.ofSeconds(5)));

        assertTrue(schedulerLockService.release("m1", "owner-b"));
        assertEquals(LockAcquireOutcome.ACQUIRED,
                schedulerLockService.acquire("m1", "owner-a", Duration.ofSeconds(5)));
    }

    @Test
    void expiredOwnerCannotRenewAfterLeaseTimeout() {
        assertEquals(LockAcquireOutcome.ACQUIRED,
                schedulerLockService.acquire("m2", "owner-a", Duration.ofSeconds(2)));
        mutableClock.advanceSeconds(3);

        assertFalse(schedulerLockService.renew("m2", "owner-a", Duration.ofSeconds(2)));
        assertEquals(LockAcquireOutcome.STOLEN,
                schedulerLockService.acquire("m2", "owner-b", Duration.ofSeconds(2)));
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
