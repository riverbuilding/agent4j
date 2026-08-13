package com.agent4j.cli;

record InteractiveCommandResult(boolean handled, boolean exit) {
    static InteractiveCommandResult handledResult() { return new InteractiveCommandResult(true, false); }
    static InteractiveCommandResult exitResult() { return new InteractiveCommandResult(true, true); }
    static InteractiveCommandResult notACommand() { return new InteractiveCommandResult(false, false); }
}
