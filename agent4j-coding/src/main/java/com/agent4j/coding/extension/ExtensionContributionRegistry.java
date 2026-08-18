package com.agent4j.coding.extension;

import com.agent4j.core.tool.Tool;
import com.agent4j.core.tool.ToolExecutionHook;
import com.agent4j.core.tool.ToolSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Resolves explicit Java extension contributions in extension and registration order. */
public final class ExtensionContributionRegistry {
    private ExtensionContributionRegistry() {
    }

    public static ResolvedExtensionContributions resolve(
            List<? extends AgentExtension> extensions,
            ExtensionContext context
    ) throws Exception {
        Objects.requireNonNull(extensions, "extensions");
        Objects.requireNonNull(context, "context");

        List<ExtensionToolContribution> tools = new ArrayList<>();
        List<ExtensionHookContribution> hooks = new ArrayList<>();
        List<ExtensionCommandContribution> commands = new ArrayList<>();
        List<ExtensionLifecycleListenerContribution> lifecycleListeners = new ArrayList<>();
        Set<String> extensionNames = new HashSet<>();
        Set<String> toolNames = new HashSet<>();
        Set<String> hookNames = new HashSet<>();
        Set<String> commandNames = new HashSet<>();
        Set<String> lifecycleListenerNames = new HashSet<>();

        for (AgentExtension extension : extensions) {
            Objects.requireNonNull(extension, "extensions must not contain null");
            String extensionName = requireName(extension.name(), "extension name");
            registerName(extensionNames, extensionName, "extension");
            extension.register(context, new Registrar(
                    extensionName,
                    tools,
                    hooks,
                    commands,
                    lifecycleListeners,
                    toolNames,
                    hookNames,
                    commandNames,
                    lifecycleListenerNames));
        }
        return new ResolvedExtensionContributions(tools, hooks, commands, lifecycleListeners);
    }

    private static String requireName(String name, String type) {
        Objects.requireNonNull(name, type);
        if (name.isBlank()) {
            throw new IllegalArgumentException(type + " must not be blank");
        }
        return name;
    }

    private static void registerName(Set<String> names, String name, String type) {
        if (!names.add(name)) {
            throw new IllegalArgumentException("duplicate " + type + " name: " + name);
        }
    }

    private record Registrar(
            String extensionName,
            List<ExtensionToolContribution> tools,
            List<ExtensionHookContribution> hooks,
            List<ExtensionCommandContribution> commands,
            List<ExtensionLifecycleListenerContribution> lifecycleListeners,
            Set<String> toolNames,
            Set<String> hookNames,
            Set<String> commandNames,
            Set<String> lifecycleListenerNames
    ) implements ExtensionContributionRegistrar {
        @Override
        public void registerTool(ToolSpec specification, Tool tool) {
            Objects.requireNonNull(specification, "specification");
            Objects.requireNonNull(tool, "tool");
            registerName(toolNames, specification.name(), "tool");
            tools.add(new ExtensionToolContribution(extensionName, specification, tool));
        }

        @Override
        public void registerHook(String name, ToolExecutionHook hook) {
            registerName(hookNames, requireName(name, "hook name"), "hook");
            hooks.add(new ExtensionHookContribution(extensionName, name, Objects.requireNonNull(hook, "hook")));
        }

        @Override
        public void registerCommand(CodingExtensionCommand command) {
            Objects.requireNonNull(command, "command");
            registerName(commandNames, command.name(), "command");
            commands.add(new ExtensionCommandContribution(extensionName, command));
        }

        @Override
        public void registerLifecycleListener(String name, ExtensionLifecycleListener listener) {
            registerName(lifecycleListenerNames, requireName(name, "lifecycle listener name"), "lifecycle listener");
            lifecycleListeners.add(new ExtensionLifecycleListenerContribution(
                    extensionName,
                    name,
                    Objects.requireNonNull(listener, "listener")));
        }
    }
}
