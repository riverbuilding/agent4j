package com.agent4j.cli;

import java.io.PrintWriter;
import java.io.Reader;
import java.util.Objects;

/** Terminal I/O boundary for interactive mode. */
public record InteractiveTerminal(Reader input, PrintWriter out, PrintWriter err, boolean ansiEnabled) {
    public InteractiveTerminal(Reader input, PrintWriter out, PrintWriter err) {
        this(input, out, err, false);
    }

    public InteractiveTerminal {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
    }

    public static InteractiveTerminal system(Reader input, PrintWriter out, PrintWriter err) {
        boolean disabled = System.getenv("NO_COLOR") != null || "dumb".equals(System.getenv("TERM"));
        return new InteractiveTerminal(input, out, err, !disabled && System.console() != null);
    }
}
