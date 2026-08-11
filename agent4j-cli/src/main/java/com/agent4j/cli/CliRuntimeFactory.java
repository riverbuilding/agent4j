package com.agent4j.cli;

@FunctionalInterface
public interface CliRuntimeFactory {
    CliRuntime create(CliRuntimeRequest request) throws Exception;
}
