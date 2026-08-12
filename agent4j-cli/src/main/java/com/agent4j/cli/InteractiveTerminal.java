package com.agent4j.cli;

import java.io.PrintWriter;
import java.io.Reader;
import java.util.Objects;

/** Terminal I/O boundary for interactive mode. */
public record InteractiveTerminal(Reader input, PrintWriter out, PrintWriter err) {
    public InteractiveTerminal {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
    }
}
