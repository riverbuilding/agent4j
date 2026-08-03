package com.agent4j.coding.runtime;

import com.agent4j.ai.AiProviderSelection;
import com.agent4j.ai.AiResolvedAuth;
import com.agent4j.ai.AiStreamOptions;
import com.agent4j.core.compaction.CompactionConfig;
import com.agent4j.coding.session.SessionManager;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record ManualCompactionRequest(
        SessionManager sessionManager,
        AiProviderSelection selection,
        AiResolvedAuth auth,
        Path cwd,
        String systemPrompt,
        CompactionConfig config,
        String focusInstructions,
        AiStreamOptions options
) {
    public ManualCompactionRequest {
        Objects.requireNonNull(sessionManager, "sessionManager");
        Objects.requireNonNull(selection, "selection");
        auth = auth == null ? AiResolvedAuth.none() : auth;
        config = config == null ? CompactionConfig.defaults() : config;
        options = options == null ? AiStreamOptions.defaults() : options;
    }

    public Optional<Path> optionalCwd() {
        if (cwd != null) {
            return Optional.of(cwd.toAbsolutePath().normalize());
        }
        return sessionManager.document()
                .header()
                .header()
                .flatMap(header -> Optional.ofNullable(header.cwd()))
                .map(Path::of)
                .map(path -> path.toAbsolutePath().normalize());
    }
}
