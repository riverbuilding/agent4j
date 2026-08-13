package com.agent4j.cli;

import com.agent4j.coding.sdk.AgentSession;
import sun.misc.Signal;
import sun.misc.SignalHandler;

/** Temporarily maps terminal SIGINT to cancellation of the active session run. */
final class InteractiveInterruptHandler implements AutoCloseable {
    private final Signal signal;
    private final SignalHandler previous;

    private InteractiveInterruptHandler(Signal signal, SignalHandler previous) {
        this.signal = signal;
        this.previous = previous;
    }

    static InteractiveInterruptHandler install(AgentSession session) {
        try {
            Signal signal = new Signal("INT");
            SignalHandler previous = Signal.handle(signal, ignored -> session.abort("cancelled by Ctrl-C"));
            return new InteractiveInterruptHandler(signal, previous);
        } catch (IllegalArgumentException unsupported) {
            return new InteractiveInterruptHandler(null, null);
        }
    }

    @Override
    public void close() {
        if (signal != null && previous != null) {
            Signal.handle(signal, previous);
        }
    }
}
