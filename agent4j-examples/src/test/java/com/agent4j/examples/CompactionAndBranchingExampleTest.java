package com.agent4j.examples;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompactionAndBranchingExampleTest {
    @Test
    void keepsOnlyTheLatestTwoMessagesWhenManuallyCompactingTheWalkthrough() {
        assertThat(CompactionAndBranchingExample.compactionConfig().keepTokens()).isZero();
        assertThat(CompactionAndBranchingExample.compactionConfig().keepMessages()).isEqualTo(2);
    }
}
