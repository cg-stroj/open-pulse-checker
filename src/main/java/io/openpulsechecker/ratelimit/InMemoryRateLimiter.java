package io.openpulsechecker.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryRateLimiter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final RateLimitProperties properties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public InMemoryRateLimiter(RateLimitProperties properties, Clock clock, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    public synchronized RateLimitDecision tryConsume(String key) {
        long now = clock.millis();
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(properties.capacity(), now));
        bucket.refill(now, properties);
        if (bucket.tokens > 0) {
            bucket.tokens -= 1;
            return new RateLimitDecision(true, 0);
        }
        meterRegistry.counter("openpulse.rate_limit.hits").increment();
        long retryAfter = Math.max(1, (bucket.nextRefillAt - now + 999) / 1000);
        return new RateLimitDecision(false, retryAfter);
    }

    static final class Bucket {
        private int tokens;
        private long nextRefillAt;

        private Bucket(int capacity, long now) {
            this.tokens = Math.max(1, capacity);
            this.nextRefillAt = now;
        }

        private void refill(long now, RateLimitProperties properties) {
            long periodMs = Math.max(1, properties.refillPeriodSeconds()) * 1000L;
            if (now < nextRefillAt) {
                return;
            }
            long periods = ((now - nextRefillAt) / periodMs) + 1;
            int newTokens = (int) Math.min(properties.capacity(), (long) tokens + periods * Math.max(1, properties.refillTokens()));
            tokens = newTokens;
            nextRefillAt += periods * periodMs;
        }
    }
}
