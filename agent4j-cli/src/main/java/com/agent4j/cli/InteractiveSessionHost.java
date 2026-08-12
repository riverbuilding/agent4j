package com.agent4j.cli;

import com.agent4j.coding.sdk.AgentSession;

import java.util.List;

@FunctionalInterface
interface InteractiveSessionHost {
    int run(AgentSession session, InteractiveTerminal terminal, List<String> initialMessages) throws Exception;
}
