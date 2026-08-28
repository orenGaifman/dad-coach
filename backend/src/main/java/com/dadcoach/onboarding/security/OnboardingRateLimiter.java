package com.dadcoach.onboarding.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Rate limiter for the onboarding flow.
 * 
 * <p>Limits: IP-based (10 attempts/hour) and phone-based (5 attempts/hour).</p>
 * 
 * <p>Enable/disable via property: {@code onboarding.rate-limit.enabled=true}</p>
 */
@Component
public class OnboardingRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(OnboardingRateLimiter.class);

    private static final int IP_LIMIT_PER_HOUR = 10;
    private static final int PHONE_LIMIT_PER_HOUR = 5;
    private static final Duration WINDOW_DURATION = Duration.ofHours(1);

    private static final String KEY_TYPE_IP = "IP";
    private static final String KEY_TYPE_PHONE = "PHONE";

    private final RateLimitEntryRepository repository;
    private final boolean rateLimitingEnabled;

    public OnboardingRateLimiter(
            RateLimitEntryRepository repository,
            @Value("${onboarding.rate-limit.enabled:true}") boolean rateLimitingEnabled) {
        this.repository = repository;
        this.rateLimitingEnabled = rateLimitingEnabled;
    }

    /**
     * Checks if the given IP is within the rate limit for invitation validation.
     * Returns a RateLimitResult indicating whether the request is allowed.
     *
     * @param ipAddress the client IP address
     * @return RateLimitResult with allowed status and retry info
     */
    @Transactional
    public RateLimitResult checkIpLimit(String ipAddress) {
        if (!rateLimitingEnabled) {
            return RateLimitResult.allowed(Integer.MAX_VALUE);
        }
        return checkLimit(KEY_TYPE_IP, ipAddress, IP_LIMIT_PER_HOUR);
    }

    /**
     * Checks if the given phone number is within the rate limit for registration.
     *
     * @param phoneNumber the phone number (E.164 format)
     * @return RateLimitResult with allowed status and retry info
     */
    @Transactional
    public RateLimitResult checkPhoneLimit(String phoneNumber) {
        if (!rateLimitingEnabled) {
            return RateLimitResult.allowed(Integer.MAX_VALUE);
        }
        return checkLimit(KEY_TYPE_PHONE, phoneNumber, PHONE_LIMIT_PER_HOUR);
    }

    /**
     * Records an attempt for the given key without checking the limit.
     * Use this after a successful check to record the actual attempt.
     */
    @Transactional
    public void recordAttempt(String keyType, String keyValue) {
        Instant windowStart = computeWindowStart(Instant.now());
        repository.findByKeyTypeAndKeyValueAndWindowStart(keyType, keyValue, windowStart)
            .ifPresentOrElse(
                RateLimitEntry::incrementAttempts,
                () -> repository.save(new RateLimitEntry(keyType, keyValue, windowStart))
            );
    }

    private RateLimitResult checkLimit(String keyType, String keyValue, int maxAttempts) {
        Instant now = Instant.now();
        Instant windowStart = computeWindowStart(now);

        var entryOpt = repository.findByKeyTypeAndKeyValueAndWindowStart(keyType, keyValue, windowStart);

        if (entryOpt.isEmpty()) {
            // First attempt in this window — create entry and allow
            repository.save(new RateLimitEntry(keyType, keyValue, windowStart));
            return RateLimitResult.allowed(maxAttempts - 1);
        }

        RateLimitEntry entry = entryOpt.get();
        if (entry.getAttemptCount() >= maxAttempts) {
            // Limit exceeded
            long secondsUntilReset = Duration.between(now, windowStart.plus(WINDOW_DURATION)).getSeconds();
            int retryAfterSeconds = (int) Math.max(secondsUntilReset, 0);
            log.warn("Rate limit exceeded for {} '{}': {} attempts in current window",
                keyType, maskKeyValue(keyType, keyValue), entry.getAttemptCount());
            return RateLimitResult.blocked(retryAfterSeconds);
        }

        // Within limit — increment and allow
        entry.incrementAttempts();
        return RateLimitResult.allowed(maxAttempts - entry.getAttemptCount());
    }

    /**
     * Computes the window start by truncating to the current hour.
     */
    Instant computeWindowStart(Instant now) {
        long epochSeconds = now.getEpochSecond();
        long windowSeconds = WINDOW_DURATION.getSeconds();
        long windowStart = (epochSeconds / windowSeconds) * windowSeconds;
        return Instant.ofEpochSecond(windowStart);
    }

    private String maskKeyValue(String keyType, String keyValue) {
        if (KEY_TYPE_PHONE.equals(keyType) && keyValue.length() > 4) {
            return "****" + keyValue.substring(keyValue.length() - 4);
        }
        return keyValue;
    }
}
