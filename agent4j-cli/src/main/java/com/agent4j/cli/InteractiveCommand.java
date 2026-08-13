package com.agent4j.cli;

@FunctionalInterface
interface InteractiveCommand {
    InteractiveCommandResult execute(String arguments) throws Exception;
}
