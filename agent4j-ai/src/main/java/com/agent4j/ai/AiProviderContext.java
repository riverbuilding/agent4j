package com.agent4j.ai;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record AiProviderContext(
        Optional<String> sessionId,
        Optional<String> turnId,
        Optional<Path> cwd,
        AiResolvedAuth auth,
        Map<String, String> environment,
        Map<String, Object> attributes
) {
    public AiProviderContext {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(turnId, "turnId");
        Objects.requireNonNull(cwd, "cwd");
        Objects.requireNonNull(auth, "auth");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(attributes, "attributes");
        cwd = cwd.map(path -> path.toAbsolutePath().normalize());
        environment = Map.copyOf(environment);
        attributes = Map.copyOf(attributes);
    }

    public static AiProviderContext empty() {
        return new AiProviderContext(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                AiResolvedAuth.none(),
                Map.of(),
                Map.of());
    }
}
