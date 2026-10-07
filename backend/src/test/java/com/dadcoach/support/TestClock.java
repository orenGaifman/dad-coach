package com.dadcoach.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock tests set explicitly, so time-of-day and week rules never depend on when the suite runs. */
public class TestClock extends Clock {

    private volatile Instant now = Instant.parse("2026-11-03T10:00:00Z"); // a Tuesday, 12:00 in Israel

    public void set(Instant instant) {
        this.now = instant;
    }

    public void advance(Duration by) {
        this.now = now.plus(by);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
