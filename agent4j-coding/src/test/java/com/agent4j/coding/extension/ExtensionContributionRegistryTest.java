package com.agent4j.coding.extension;

import com.agent4j.core.tool.Tool;
import com.agent4j.core.tool.ToolExecutionHook;
import com.agent4j.core.tool.ToolSpec;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtensionContributionRegistryTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @Test
    void resolvesContributionsInExtensionAndRegistrationOrder() throws Exception {
        AgentExtension first = extension("first", registrar -> {
            registrar.registerTool(toolSpec("first-tool"), tool());
            registrar.registerHook("first-hook", new ToolExecutionHook() {
            });
            registrar.registerCommand(command("first-command"));
            registrar.registerLifecycleListener("first-listener", new ExtensionLifecycleListener() {
            });
            registrar.registerTool(toolSpec("second-tool"), tool());
        });
        AgentExtension second = extension("second", registrar ->
                registrar.registerCommand(command("second-command")));

        ResolvedExtensionContributions contributions = ExtensionContributionRegistry.resolve(
                List.of(first, second),
                new ExtensionContext(Path.of("workspace"), null, true));

        assertThat(contributions.tools())
                .extracting(contribution -> contribution.specification().name())
                .containsExactly("first-tool", "second-tool");
        assertThat(contributions.hooks()).extracting(ExtensionHookContribution::name)
                .containsExactly("first-hook");
        assertThat(contributions.commands()).extracting(contribution -> contribution.command().name())
                .containsExactly("first-command", "second-command");
        assertThat(contributions.lifecycleListeners()).extracting(ExtensionLifecycleListenerContribution::name)
                .containsExactly("first-listener");
        assertThatThrownBy(() -> contributions.tools().add(contributions.tools().getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDuplicateNamesInEachContributionNamespace() {
        ExtensionContext context = new ExtensionContext(Path.of("workspace"), null, false);

        assertThatThrownBy(() -> ExtensionContributionRegistry.resolve(List.of(
                extension("first", registrar -> registrar.registerTool(toolSpec("read"), tool())),
                extension("second", registrar -> registrar.registerTool(toolSpec("read"), tool()))), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("duplicate tool name: read");
        assertThatThrownBy(() -> ExtensionContributionRegistry.resolve(List.of(
                extension("same", registrar -> {
                }),
                extension("same", registrar -> {
                })), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("duplicate extension name: same");
    }

    private static AgentExtension extension(String name, Registration registration) {
        return new AgentExtension() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void register(ExtensionContext context, ExtensionContributionRegistrar registrar) throws Exception {
                registration.register(registrar);
            }
        };
    }

    private static ToolSpec toolSpec(String name) {
        return new ToolSpec(name, name, JSON.objectNode());
    }

    private static Tool tool() {
        return (call, context) -> null;
    }

    private static CodingExtensionCommand command(String name) {
        return new CodingExtensionCommand(name, null, (arguments, context) -> {
        });
    }

    @FunctionalInterface
    private interface Registration {
        void register(ExtensionContributionRegistrar registrar) throws Exception;
    }
}
