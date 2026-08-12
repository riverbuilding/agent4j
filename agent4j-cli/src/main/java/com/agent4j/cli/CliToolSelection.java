package com.agent4j.cli;

import java.util.List;
import java.util.Optional;

record CliToolSelection(
        Optional<List<String>> included,
        List<String> excluded,
        boolean noTools,
        boolean noBuiltinTools
) {
    CliToolSelection {
        included = included == null ? Optional.empty() : included.map(List::copyOf);
        excluded = excluded == null ? List.of() : List.copyOf(excluded);
    }

    static CliToolSelection defaults() {
        return new CliToolSelection(Optional.empty(), List.of(), false, false);
    }
}
