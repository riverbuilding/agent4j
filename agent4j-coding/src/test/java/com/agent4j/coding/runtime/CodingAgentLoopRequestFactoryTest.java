package com.agent4j.coding.runtime;

import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.agent4j.core.message.ContentBlocks;
import com.agent4j.core.message.TextBlock;
import com.agent4j.core.runtime.AbortController;
import com.agent4j.core.runtime.AgentLoopOptions;
import com.agent4j.core.runtime.AgentLoopRequest;
import com.agent4j.core.runtime.LiveAgentQueues;
import com.agent4j.core.runtime.QueueKind;
import com.agent4j.core.runtime.QueueMode;
import com.agent4j.core.runtime.ToolExecutionMode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CodingAgentLoopRequestPreparerTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-30T10:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @Test
    void preparesLoopRequestWithDiscoveredSystemPromptAndResourceDiscovery() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        write(home.resolve(".pi/agent/SYSTEM.md"), "global system\n");
        write(home.resolve(".pi/agent/APPEND_SYSTEM.md"), "global append\n");
        write(cwd.resolve(".pi/SYSTEM.md"), "project system\n");
        write(cwd.resolve("AGENTS.md"), "project context\n");
        write(cwd.resolve(".pi/skills/review/SKILL.md"), """
                ---
                name: review
                description: Review code.
                ---
                # Review
                """);
        AgentMessage user = message("user-1", AgentMessageRole.USER, "summarize");
        AgentMessage steering = message("steer-1", AgentMessageRole.USER, "be brief");
        AgentLoopRequest request = new AgentLoopRequest(
                "session-1",
                "turn-1",
                user.id(),
                List.of(user),
                cwd,
                clock,
                new AbortController().signal(),
                AgentLoopOptions.builder()
                        .toolAttributes(Map.of("workspace", "repo"))
                        .maxToolRounds(3)
                        .maxModelRetries(2)
                        .toolExecutionMode(ToolExecutionMode.SEQUENTIAL)
                        .promptMessages(List.of(user))
                        .steeringMode(QueueMode.ALL)
                        .build(),
                new LiveAgentQueues(List.of(steering), List.of()));

        PreparedAgentLoopRequest prepared = new CodingAgentLoopRequestPreparer().prepare(request, home);

        assertThat(prepared.discovery().systemPrompt()).hasValueSatisfying(system ->
                assertThat(system.content()).isEqualTo("project system\n"));
        assertThat(prepared.request().systemPrompt())
                .contains("project system")
                .contains("global append")
                .contains("project context")
                .contains("<name>review</name>");
        assertThat(prepared.request().messages()).containsExactly(user);
        assertThat(prepared.request().promptMessages()).containsExactly(user);
        assertThat(prepared.request().liveQueues().size(QueueKind.STEER)).isEqualTo(1);
        assertThat(prepared.request().toolAttributes()).containsEntry("workspace", "repo");
        assertThat(prepared.request().toolExecutionMode()).isEqualTo(ToolExecutionMode.SEQUENTIAL);
        assertThat(prepared.request().maxToolRounds()).isEqualTo(3);
        assertThat(prepared.request().maxModelRetries()).isEqualTo(2);

    }

    @Test
    void appliesTheDefaultSystemPromptWhenNoResourcesAreDiscovered() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        AgentMessage user = message("user-1", AgentMessageRole.USER, "hello");
        AgentLoopRequest request = new AgentLoopRequest(
                "session-1",
                "turn-1",
                user.id(),
                List.of(user),
                cwd,
                clock,
                new AbortController().signal(),
                AgentLoopOptions.builder()
                        .maxToolRounds(1)
                        .promptMessages(List.of(user))
                        .build());

        PreparedAgentLoopRequest prepared = new CodingAgentLoopRequestPreparer().prepare(request, home);

        assertThat(prepared.request().systemPrompt()).contains("agent4j-coding-v1");
        assertThat(prepared.discovery().contextFiles()).isEmpty();
    }

    @Test
    void appliesRetryAndTimeoutDefaultsFromSettings() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        write(home.resolve(".pi/agent/settings.json"), """
                {
                  "httpIdleTimeoutMs": 300000,
                  "retry": {
                    "maxRetries": 4
                  }
                }
                """);
        AgentMessage user = message("user-1", AgentMessageRole.USER, "hello");
        AgentLoopRequest request = new AgentLoopRequest(
                "session-1",
                "turn-1",
                user.id(),
                List.of(user),
                cwd,
                clock,
                new AbortController().signal(),
                AgentLoopOptions.builder()
                        .maxToolRounds(1)
                        .promptMessages(List.of(user))
                        .build());

        PreparedAgentLoopRequest prepared = new CodingAgentLoopRequestPreparer().prepare(request, home);

        assertThat(prepared.request().maxModelRetries()).isEqualTo(4);
        assertThat(prepared.request().modelTimeout()).contains(Duration.ofMillis(300000));
    }

    @Test
    void preservesRequestRetryAndTimeoutOverSettingsDefaults() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        write(home.resolve(".pi/agent/settings.json"), """
                {
                  "httpIdleTimeoutMs": 300000,
                  "retry": {
                    "maxRetries": 4
                  }
                }
                """);
        AgentMessage user = message("user-1", AgentMessageRole.USER, "hello");
        AgentLoopRequest request = new AgentLoopRequest(
                "session-1",
                "turn-1",
                user.id(),
                List.of(user),
                cwd,
                clock,
                new AbortController().signal(),
                AgentLoopOptions.builder()
                        .maxToolRounds(1)
                        .maxModelRetries(2)
                        .modelTimeout(java.util.Optional.of(Duration.ofSeconds(10)))
                        .promptMessages(List.of(user))
                        .build());

        PreparedAgentLoopRequest prepared = new CodingAgentLoopRequestPreparer().prepare(request, home);

        assertThat(prepared.request().maxModelRetries()).isEqualTo(2);
        assertThat(prepared.request().modelTimeout()).contains(Duration.ofSeconds(10));
    }

    private AgentMessage message(String id, AgentMessageRole role, String text) {
        return new AgentMessage(
                id,
                null,
                Instant.now(clock),
                role,
                ContentBlocks.toJsonArray(List.of(new TextBlock(text, null))),
                JSON.objectNode());
    }

    private static void write(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
