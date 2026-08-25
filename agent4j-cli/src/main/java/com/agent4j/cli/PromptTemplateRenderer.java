package com.agent4j.cli;

import com.agent4j.coding.resource.PromptTemplate;
import com.agent4j.coding.resource.ResourceDiscovery;

import java.util.List;
import java.util.Objects;

/** Renders discovered text prompt templates for CLI and interactive prompts. */
final class PromptTemplateRenderer {
    private PromptTemplateRenderer() {
    }

    static String render(ResourceDiscovery discovery, String name, List<String> arguments) {
        Objects.requireNonNull(discovery, "discovery");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(arguments, "arguments");
        PromptTemplate template = discovery.promptTemplates().stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown prompt template '" + name + "'; available: "
                        + discovery.promptTemplates().stream().map(PromptTemplate::name).sorted().toList()));
        String suppliedArguments = String.join(" ", arguments).strip();
        if (template.content().contains("$ARGUMENTS")) {
            return template.content().replace("$ARGUMENTS", suppliedArguments).strip();
        }
        return suppliedArguments.isEmpty() ? template.content().strip() : template.content().strip() + "\n\n" + suppliedArguments;
    }
}
