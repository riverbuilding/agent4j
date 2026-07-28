package com.agent4j.core.runtime;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class AbortController {
    private final AtomicReference<String> reason = new AtomicReference<>();

    public AbortSignal signal() {
        return new AbortSignal() {
            @Override
            public boolean aborted() {
                return reason.get() != null;
            }

            @Override
            public Optional<String> reason() {
                return Optional.ofNullable(reason.get());
            }
        };
    }

    public boolean abort(String abortReason) {
        return reason.compareAndSet(null, abortReason == null || abortReason.isBlank() ? "aborted" : abortReason);
    }
}
