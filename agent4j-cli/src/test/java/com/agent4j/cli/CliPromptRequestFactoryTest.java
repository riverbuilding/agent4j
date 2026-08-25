package com.agent4j.cli;

import com.agent4j.ai.AiModelReference;
import com.agent4j.core.runtime.AbortController;
import com.agent4j.core.runtime.QueueMode;
import com.agent4j.core.runtime.ToolExecutionMode;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CliPromptRequestFactoryTest {
    @Test
    void appliesSharedCliPromptDefaults() {
        AbortController controller = new AbortController();
        var signal = controller.signal();

        var request = CliPromptRequestFactory.create(
                "hello", Optional.of(new AiModelReference("openai", "gpt-test")), Optional.of(signal));

        assertThat(request.prompt()).isEqualTo("hello");
        assertThat(request.model()).contains(new AiModelReference("openai", "gpt-test"));
        assertThat(request.maxToolRounds()).isEqualTo(CliPromptRequestFactory.DEFAULT_MAX_TOOL_ROUNDS);
        assertThat(request.maxModelRetries()).isZero();
        assertThat(request.toolExecutionMode()).isEqualTo(ToolExecutionMode.PARALLEL);
        assertThat(request.steeringMode()).isEqualTo(QueueMode.ONE_AT_A_TIME);
        assertThat(request.followUpMode()).isEqualTo(QueueMode.ONE_AT_A_TIME);
        assertThat(request.abortSignal()).containsSame(signal);
    }

    @Test
    void leavesSystemPromptResolutionToTheRuntime() {
        assertThat(CliPromptRequestFactory.create(
                "inspect the project", Optional.empty(), Optional.empty()).systemPrompt()).isEmpty();
    }
}
