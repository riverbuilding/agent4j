# Java Extension SPI

Phase 13 exposes a Java-only extension SPI in
`com.agent4j.coding.extension`. It lets an embedding application add coding
agent capabilities without executing project code or invoking a JavaScript
runtime.

## Implementing an extension

Implement `AgentExtension`, give it a unique stable name, and register it with
an `ExtensionLoader` supplied to `CodingAgentRuntime`.

```java
AgentExtension greeting = new AgentExtension() {
    @Override
    public String name() {
        return "example.greeting";
    }

    @Override
    public void register(ExtensionContext context, ExtensionContributionRegistrar registrar) {
        registrar.registerTool(greetingSpec, greetingTool);
    }
};

CodingAgentRuntime runtime = CodingAgentRuntime.builder()
        .extensionLoader(ExtensionLoader.builder().addExtension(greeting).build())
        .extensionContext(new ExtensionContext(workspace, null, true))
        .build();
```

Extensions can contribute tools, tool-execution hooks, interactive commands,
agent-start hooks, context transforms, provider hooks, and session lifecycle
listeners. Contributions preserve extension and registration order; duplicate
extension or contribution names fail runtime setup deterministically. See
[ADR 0003](adr/0003-java-extension-spi.md) for lifecycle ordering and failure
handling.

## Scope and project trust

`AgentExtension.scope()` identifies an extension as either
`ExtensionScope.APPLICATION` (the default) or `ExtensionScope.PROJECT`.
Applications and global classpath providers use application scope. An embedding
application may mark an explicitly supplied extension as project-scoped when
its use belongs to the active project:

```java
@Override
public ExtensionScope scope() {
    return ExtensionScope.PROJECT;
}

@Override
public boolean requiresProjectTrust() {
    return true;
}
```

The runtime passes its `ExtensionContext` to `ExtensionLoader`. If
`projectTrusted()` is `false`, project-scoped extensions are not activated and
none of their contributions are registered. Application-scoped extensions are
still controlled by the embedding application. The `requiresProjectTrust()`
method is a forward-compatible declaration for later project-resource and
package policies; in this release, project scope is the enforced activation
gate.

This is deliberately a trust boundary, not a sandbox. An active Java extension
runs with the permissions of the embedding JVM. Hosts should therefore include
only extensions they trust, construct the runtime with the correct project
trust decision, and avoid treating project-controlled configuration as
application-classpath code.

## Discovery and first-release limits

`ExtensionLoader` accepts explicit Java extension objects and can discover
`AgentExtension` providers through `ServiceLoader` on the application class
loader. It does not scan a project directory or classpath chosen by a project.

The following are explicit non-goals for Phase 13 and are deferred to Phase 14
package-bridge design work:

- TypeScript or JavaScript extension execution
- Node subprocesses or any dynamic project-code loading
- package installation, update, or reconciliation
- compatibility with the PI package format
- project-local extension discovery and trust prompting

Phase 14 must define a package compatibility decision, sandboxing approach,
and trust-prompt model before any of those capabilities are added.
