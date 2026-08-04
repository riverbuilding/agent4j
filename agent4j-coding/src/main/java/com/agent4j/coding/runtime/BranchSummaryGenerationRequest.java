package com.agent4j.coding.runtime;

import com.agent4j.ai.AiProviderSelection;
import com.agent4j.ai.AiResolvedAuth;
import com.agent4j.ai.AiStreamOptions;
import com.agent4j.coding.session.SessionManager;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record BranchSummaryGenerationRequest(
        SessionManager sourceSessionManager,
        SessionManager targetSessionManager,
        AiProviderSelection selection,
        AiResolvedAuth auth,
        Path cwd,
        String systemPrompt,
        String summaryPrompt,
        String focusInstructions,
        AiStreamOptions options
) {
    public BranchSummaryGenerationRequest {
        Objects.requireNonNull(sourceSessionManager, "sourceSessionManager");
        Objects.requireNonNull(targetSessionManager, "targetSessionManager");
        Objects.requireNonNull(selection, "selection");
        auth = auth == null ? AiResolvedAuth.none() : auth;
        options = options == null ? AiStreamOptions.defaults() : options;
    }

    public Optional<Path> optionalCwd() {
        if (cwd != null) {
            return Optional.of(cwd.toAbsolutePath().normalize());
        }
        return sourceSessionManager.document()
                .header()
                .header()
                .flatMap(header -> Optional.ofNullable(header.cwd()))
                .map(Path::of)
                .map(path -> path.toAbsolutePath().normalize());
    }
}
