package com.agent4j.cli;

import com.agent4j.core.event.AgentEvent;
import java.util.Objects;

/** Renders one interactive session's runtime events without replaying completed text. */
final class InteractiveEventRenderer {
    private final TerminalRenderingLayer renderingLayer;

    InteractiveEventRenderer(InteractiveTerminal terminal) {
        this.renderingLayer = TerminalRenderingLayer.Factory.create(Objects.requireNonNull(terminal, "terminal"));
    }

    synchronized void render(AgentEvent event) {
        renderingLayer.render(event);
    }
}
