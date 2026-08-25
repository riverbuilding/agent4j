package com.agent4j.cli;

import java.util.List;

@FunctionalInterface
interface InteractiveSessionRunner {
    int run(InteractiveSessionController controller, InteractiveTerminal terminal, List<String> initialMessages) throws Exception;
}
