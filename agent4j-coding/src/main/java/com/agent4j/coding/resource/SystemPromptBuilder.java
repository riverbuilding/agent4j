package com.agent4j.coding.resource;

import com.agent4j.core.tool.ToolSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class SystemPromptBuilder {
    public String build(ResourceDiscovery discovery) {
        return build(discovery, List.of(), Optional.empty(), List.of());
    }

    /**
     * Composes the coding-agent prompt using PI's default-prompt structure. A discovered or explicit
     * system prompt replaces the built-in baseline; append prompts, context files, and eligible skills
     * remain additive.
     */
    public String build(
            ResourceDiscovery discovery,
            Collection<ToolSpec> toolSpecifications,
            Optional<String> explicitSystemPrompt,
            List<String> explicitAppendPrompts
    ) {
        Objects.requireNonNull(discovery, "discovery");
        Objects.requireNonNull(toolSpecifications, "toolSpecifications");
        explicitSystemPrompt = explicitSystemPrompt == null ? Optional.empty() : explicitSystemPrompt;
        explicitAppendPrompts = explicitAppendPrompts == null ? List.of() : List.copyOf(explicitAppendPrompts);

        Optional<String> replacementPrompt = explicitSystemPrompt
                .or(() -> discovery.systemPrompt().map(ResourceFile::content));
        List<String> sections = new ArrayList<>();
        if (replacementPrompt.isPresent()) {
            sections.add(replacementPrompt.orElseThrow());
        } else {
            sections.add(defaultPrompt(toolSpecifications));
        }
        discovery.appendSystemFiles().stream()
                .map(ResourceFile::content)
                .forEach(sections::add);
        explicitAppendPrompts.forEach(sections::add);
        if (!discovery.contextFiles().isEmpty()) {
            sections.add(contextFiles(discovery.contextFiles()));
        }

        List<Skill> modelVisibleSkills = discovery.skills().stream()
                .filter(skill -> !skill.disableModelInvocation())
                .toList();
        if (!modelVisibleSkills.isEmpty()) {
            boolean readAvailable = toolSpecifications.isEmpty()
                    || toolSpecifications.stream().map(ToolSpec::name).anyMatch("read"::equals);
            if (readAvailable) {
                sections.add(skills(modelVisibleSkills));
            }
        }
        sections.add("Current working directory: " + workspace(discovery));

        return sections.stream()
                .filter(section -> !section.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private static String defaultPrompt(Collection<ToolSpec> toolSpecifications) {
        List<ToolSpec> tools = toolSpecifications.isEmpty()
                ? List.of()
                : List.copyOf(toolSpecifications);
        StringBuilder builder = new StringBuilder(DefaultCodingSystemPrompt.text())
                .append("\n\nAvailable tools:\n");
        if (tools.isEmpty()) {
            builder.append("(none)");
        }
        for (ToolSpec specification : toolSpecifications) {
            ToolSpec tool = Objects.requireNonNull(specification, "toolSpecifications must not contain null");
            builder.append("- ").append(tool.name()).append(": ").append(tool.description()).append("\n");
        }
        builder.append("\nIn addition to the tools above, you may have access to other custom tools depending on the project.\n\n")
                .append("Guidelines:\n");
        boolean hasBash = tools.stream().map(ToolSpec::name).anyMatch("bash"::equals);
        boolean hasSearchTool = tools.stream().map(ToolSpec::name)
                .anyMatch(name -> name.equals("grep") || name.equals("find") || name.equals("ls"));
        if (hasBash && !hasSearchTool) {
            builder.append("- Use bash for file operations like ls, rg, find\n");
        }
        return builder.append("- Be concise in your responses\n")
                .append("- Show file paths clearly when working with files")
                .toString();
    }

    private static String workspace(ResourceDiscovery discovery) {
        return discovery.directories().projectAgentDir().getParent().toString().replace('\\', '/');
    }

    private static String contextFiles(List<ResourceFile> files) {
        StringBuilder builder = new StringBuilder("<project_context>\n\nProject-specific instructions and guidelines:\n");
        for (ResourceFile file : files) {
            builder.append("\n<project_instructions path=\"").append(attribute(file.path().toString())).append("\">\n")
                    .append(file.content())
                    .append("\n</project_instructions>\n");
        }
        return builder.append("</project_context>").toString();
    }

    private static String skills(List<Skill> skills) {
        StringBuilder builder = new StringBuilder("The following skills provide specialized instructions for specific tasks.\n")
                .append("Use the read tool to load a skill's file when the task matches its description.\n")
                .append("When a skill file references a relative path, resolve it against the skill directory and use that absolute path in tool commands.\n\n")
                .append("<available_skills>");
        for (Skill skill : skills) {
            builder.append("\n  <skill>\n")
                    .append("    <name>").append(text(skill.name())).append("</name>\n")
                    .append("    <description>").append(text(skill.description())).append("</description>\n")
                    .append("    <location>").append(text(skill.path().toString())).append("</location>\n")
                    .append("  </skill>");
        }
        return builder.append("\n</available_skills>").toString();
    }

    private static String attribute(String value) {
        return text(value).replace("\"", "&quot;");
    }

    private static String text(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
