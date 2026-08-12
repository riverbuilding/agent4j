package com.agent4j.cli;

import java.nio.file.Path;
import java.util.Optional;

record CliSessionOptions(
        boolean continueSession,
        boolean resume,
        boolean noSession,
        Optional<String> session,
        Optional<String> sessionId,
        Optional<String> fork,
        Optional<Path> sessionDirectory,
        Optional<String> name
) {
    CliSessionOptions {
        session = session == null ? Optional.empty() : session;
        sessionId = sessionId == null ? Optional.empty() : sessionId;
        fork = fork == null ? Optional.empty() : fork;
        sessionDirectory = sessionDirectory == null ? Optional.empty() : sessionDirectory;
        name = name == null ? Optional.empty() : name;
    }

    static CliSessionOptions defaults() {
        return new CliSessionOptions(false, false, false, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    static void validate(CliSessionOptions options) {
        if (options.fork().isPresent() && (options.session().isPresent() || options.continueSession() || options.resume() || options.noSession())) {
            throw new IllegalArgumentException("--fork cannot be combined with --session, --continue, --resume, or --no-session");
        }
        if (options.sessionId().isPresent() && (options.session().isPresent() || options.continueSession() || options.resume())) {
            throw new IllegalArgumentException("--session-id cannot be combined with --session, --continue, or --resume");
        }
        options.name().ifPresent(name -> {
            if (name.isBlank()) {
                throw new IllegalArgumentException("--name requires a non-empty value");
            }
        });
    }
}
