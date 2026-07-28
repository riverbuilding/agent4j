package com.agent4j.coding.tool;

import java.time.Duration;

public record CodingToolLimits(int maxOutputChars, int maxResultItems, Duration defaultCommandTimeout) {
    public CodingToolLimits {
        if (maxOutputChars < 1) {
            throw new IllegalArgumentException("maxOutputChars must be positive");
        }
        if (maxResultItems < 1) {
            throw new IllegalArgumentException("maxResultItems must be positive");
        }
        if (defaultCommandTimeout == null || defaultCommandTimeout.isNegative() || defaultCommandTimeout.isZero()) {
            throw new IllegalArgumentException("defaultCommandTimeout must be positive");
        }
    }

    public static CodingToolLimits defaults() {
        return new CodingToolLimits(20_000, 200, Duration.ofSeconds(30));
    }
}
