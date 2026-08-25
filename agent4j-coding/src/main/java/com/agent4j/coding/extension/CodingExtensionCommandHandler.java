package com.agent4j.coding.extension;

@FunctionalInterface
public interface CodingExtensionCommandHandler {
    void execute(String arguments, CodingExtensionContext context) throws Exception;
}
