package com.agent4j.coding.extension;

import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProviderContext;
import com.agent4j.ai.AiProviderRequest;
import com.agent4j.ai.AiStreamOptions;
import com.agent4j.ai.AiTurnRequest;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodingAgentExtensionContractTest {
    @Test
    void defaultLifecycleMethodsPreserveTheirInputs() throws Exception {
        CodingAgentExtension extension = new CodingAgentExtension() {
            @Override
            public String name() {
                return "test";
            }
        };
        CodingExtensionContext context = new CodingExtensionContext(Path.of("workspace"), null, false);
        CodingExtensionAgentStart start = new CodingExtensionAgentStart("prompt", "system");
        List<AgentMessage> messages = List.of(new AgentMessage(
                "message", null, Instant.EPOCH, AgentMessageRole.USER, null, null));
        AiProviderRequest request = new AiProviderRequest(
                new AiModel(new AiModelReference("test", "model"), "Test"),
                new AiTurnRequest(List.of(), List.of()),
                AiProviderContext.empty(),
                AiStreamOptions.defaults());

        assertThat(extension.beforeAgentStart(start, context)).isSameAs(start);
        assertThat(extension.transformContext(messages, context)).isSameAs(messages);
        assertThat(extension.beforeProviderRequest(request, context)).isSameAs(request);
        assertThat(context.optionalSessionFile()).isEmpty();
    }
}
