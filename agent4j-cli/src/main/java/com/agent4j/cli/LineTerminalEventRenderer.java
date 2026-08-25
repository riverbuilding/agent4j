package com.agent4j.cli;

/** Plain-text fallback for pipes, tests, and terminals without ANSI support. */
final class LineTerminalEventRenderer extends TerminalEventRenderer.Base {
    LineTerminalEventRenderer(InteractiveTerminal terminal) {
        super(terminal);
    }

    @Override
    protected void markdown(String value) {
        terminal.out().println(value);
        terminal.out().flush();
    }

    @Override
    protected void status(String value) {
        finishLine();
        terminal.out().println("[" + value + "]");
        terminal.out().flush();
    }

    @Override
    protected void error(String value) {
        finishLine();
        terminal.err().println("Error: " + value);
        terminal.err().flush();
    }
}
