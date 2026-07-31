package com.agent4j.coding.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public final class SystemPromptBuilder {
    public String build(ResourceDiscovery discovery) {
        Objects.requireNonNull(discovery, "discovery");

        List<String> sections = new ArrayList<>();
        discovery.systemPrompt().map(ResourceFile::content).ifPresent(sections::add);
        discovery.appendSystemFiles().stream()
                .map(ResourceFile::content)
                .forEach(sections::add);
        if (!discovery.contextFiles().isEmpty()) {
            sections.add(contextFiles(discovery.contextFiles()));
        }

        List<Skill> modelVisibleSkills = discovery.skills().stream()
                .filter(skill -> !skill.disableModelInvocation())
                .toList();
        if (!modelVisibleSkills.isEmpty()) {
            sections.add(skills(modelVisibleSkills));
        }

        return sections.stream()
                .filter(section -> !section.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private static String contextFiles(List<ResourceFile> files) {
        StringBuilder builder = new StringBuilder("<context-files>");
        for (ResourceFile file : files) {
            builder.append("\n<context-file")
                    .append(" scope=\"").append(attribute(file.scope().name().toLowerCase(Locale.ROOT))).append("\"")
                    .append(" type=\"").append(attribute(file.type().name().toLowerCase(Locale.ROOT))).append("\"")
                    .append(" path=\"").append(attribute(file.path().toString())).append("\"")
                    .append(">\n")
                    .append(text(file.content()))
                    .append("\n</context-file>");
        }
        return builder.append("\n</context-files>").toString();
    }

    private static String skills(List<Skill> skills) {
        StringBuilder builder = new StringBuilder("<skills>");
        for (Skill skill : skills) {
            builder.append("\n<skill")
                    .append(" name=\"").append(attribute(skill.name())).append("\"")
                    .append(" scope=\"").append(attribute(skill.scope().name().toLowerCase(Locale.ROOT))).append("\"")
                    .append(" path=\"").append(attribute(skill.path().toString())).append("\"");
            if (!skill.allowedTools().isEmpty()) {
                builder.append(" allowed-tools=\"")
                        .append(attribute(String.join(" ", skill.allowedTools())))
                        .append("\"");
            }
            builder.append(">")
                    .append("\n<description>").append(text(skill.description())).append("</description>");
            skill.license().ifPresent(license ->
                    builder.append("\n<license>").append(text(license)).append("</license>"));
            skill.compatibility().ifPresent(compatibility ->
                    builder.append("\n<compatibility>").append(text(compatibility)).append("</compatibility>"));
            builder.append("\n</skill>");
        }
        return builder.append("\n</skills>").toString();
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
