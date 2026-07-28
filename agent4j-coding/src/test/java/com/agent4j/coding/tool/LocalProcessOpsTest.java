package com.agent4j.coding.tool;

import com.agent4j.core.operation.ProcessResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalProcessOpsTest {
    @TempDir
    Path tempDir;

    @Test
    void runsShortLivedCommandInWorkingDirectory() throws Exception {
        ProcessResult result = new LocalProcessOps().run(
                List.of("/bin/sh", "-lc", "pwd && printf out && printf err >&2"),
                tempDir,
                Duration.ofSeconds(2));

        assertThat(result.timedOut()).isFalse();
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains(tempDir.toString()).contains("out");
        assertThat(result.stderr()).isEqualTo("err");
        assertThat(result.duration().toMillis()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void killsTimedOutCommand() throws Exception {
        ProcessResult result = new LocalProcessOps().run(
                List.of("/bin/sh", "-lc", "sleep 2"),
                tempDir,
                Duration.ofMillis(100));

        assertThat(result.timedOut()).isTrue();
        assertThat(result.exitCode()).isEqualTo(-1);
    }
}
