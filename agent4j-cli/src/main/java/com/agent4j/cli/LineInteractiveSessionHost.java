package com.agent4j.cli;

import com.agent4j.coding.sdk.AgentSession;
import com.agent4j.coding.sdk.PromptRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Minimal persistent line REPL over one SDK session. */
final class LineInteractiveSessionHost implements InteractiveSessionHost {
    private static final int DEFAULT_MAX_TOOL_ROUNDS = 20;
    private static final String PROMPT = "agent4j> ";

    @Override
    public int run(AgentSession session, InteractiveTerminal terminal, List<String> initialMessages) throws IOException {
        for (String message : initialMessages) {
            submit(session, message, terminal);
        }

        BufferedReader reader = terminal.input() instanceof BufferedReader buffered
                ? buffered
                : new BufferedReader(terminal.input());
        while (true) {
            terminal.out().print(PROMPT);
            terminal.out().flush();
            String line = reader.readLine();
            if (line == null) {
                return 0;
            }
            submit(session, line, terminal);
        }
    }

    private void submit(AgentSession session, String input, InteractiveTerminal terminal) {
        String prompt = input == null ? "" : input.strip();
        if (prompt.isEmpty()) {
            return;
        }
        try {
            session.prompt(new PromptRequest(
                    prompt,
                    Optional.empty(),
                    DEFAULT_MAX_TOOL_ROUNDS,
                    0,
                    Optional.empty(),
                    null,
                    java.util.Map.of(),
                    List.of(),
                    List.of(),
                    null,
                    null,
                    Optional.empty()));
        } catch (Exception error) {
            terminal.err().println("Error: " + error.getMessage());
            terminal.err().flush();
        }
    }

}
