package com.agent4j.cli;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

/** JLine-backed renderer for ANSI-aware terminals. */
final class JLineTerminalRenderingLayer extends TerminalRenderingLayer.Base {
    private static final AttributedStyle STATUS = AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN);
    private static final AttributedStyle ERROR = AttributedStyle.DEFAULT.foreground(AttributedStyle.RED).bold();
    private static final AttributedStyle HEADING = AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE).bold();
    private static final AttributedStyle CODE = AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);

    JLineTerminalRenderingLayer(InteractiveTerminal terminal) {
        super(terminal);
    }

    @Override
    protected void markdown(String value) {
        for (String line : value.split("\\R", -1)) {
            String rendered = line;
            AttributedStyle style = AttributedStyle.DEFAULT;
            if (line.startsWith("#")) {
                rendered = line.replaceFirst("^#+\\s*", "");
                style = HEADING;
            } else if (line.stripLeading().startsWith("`") && line.stripTrailing().endsWith("`")) {
                style = CODE;
            }
            terminal.out().println(new AttributedString(rendered, style).toAnsi());
        }
        terminal.out().flush();
    }

    @Override
    protected void status(String value) {
        finishLine();
        terminal.out().println(new AttributedString("[" + value + "]", STATUS).toAnsi());
        terminal.out().flush();
    }

    @Override
    protected void error(String value) {
        finishLine();
        terminal.err().println(new AttributedString("Error: " + value, ERROR).toAnsi());
        terminal.err().flush();
    }
}
