package com.agent4j.cli;

import com.agent4j.coding.sdk.AgentSession;

@FunctionalInterface
interface InteractiveSessionHost {
    int run(AgentSession session, InteractiveTerminal terminal) throws Exception;
}
