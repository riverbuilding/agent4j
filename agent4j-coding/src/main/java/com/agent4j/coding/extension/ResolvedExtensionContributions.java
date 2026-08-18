package com.agent4j.coding.extension;

import java.util.List;

/** Immutable, ordered contributions resolved from a supplied extension list. */
public record ResolvedExtensionContributions(
        List<ExtensionToolContribution> tools,
        List<ExtensionHookContribution> hooks,
        List<ExtensionAgentStartHookContribution> agentStartHooks,
        List<ExtensionContextTransformHookContribution> contextTransformHooks,
        List<ExtensionProviderHookContribution> providerHooks,
        List<ExtensionCommandContribution> commands,
        List<ExtensionLifecycleListenerContribution> lifecycleListeners
) {
    public ResolvedExtensionContributions {
        tools = List.copyOf(tools);
        hooks = List.copyOf(hooks);
        agentStartHooks = List.copyOf(agentStartHooks);
        contextTransformHooks = List.copyOf(contextTransformHooks);
        providerHooks = List.copyOf(providerHooks);
        commands = List.copyOf(commands);
        lifecycleListeners = List.copyOf(lifecycleListeners);
    }
}
