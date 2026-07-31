package com.agent4j.coding.runtime;

import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.agent4j.core.message.ContentBlocks;
import com.agent4j.core.message.TextBlock;
import com.agent4j.core.runtime.AbortController;
import com.agent4j.core.runtime.AgentLoopRequest;
import com.agent4j.core.runtime.QueueMode;
import com.agent4j.core.runtime.ToolExecutionMode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CodingAgentLoopRequestFactoryTest {
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
                Map.of("workspace", "repo"),
                null,
                3,
                2,
                ToolExecutionMode.SEQUENTIAL,
                List.of(user),
                List.of(steering),
                List.of(),
                QueueMode.ALL,
                QueueMode.ONE_AT_A_TIME);

        PreparedAgentLoopRequest prepared = new CodingAgentLoopRequestFactory().prepare(request, home);

        assertThat(prepared.discovery().systemPrompt()).hasValueSatisfying(system ->
                assertThat(system.content()).isEqualTo("project system\n"));
        assertThat(prepared.request().systemPrompt())
                .contains("project system")
                .contains("global append")
                .contains("project context")
                .contains("name=\"review\"");
        assertThat(prepared.request().messages()).containsExactly(user);
        assertThat(prepared.request().promptMessages()).containsExactly(user);
        assertThat(prepared.request().steeringMessages()).containsExactly(steering);
        assertThat(prepared.request().toolAttributes()).containsEntry("workspace", "repo");
        assertThat(prepared.request().toolExecutionMode()).isEqualTo(ToolExecutionMode.SEQUENTIAL);
        assertThat(prepared.request().maxToolRounds()).isEqualTo(3);
        assertThat(prepared.request().maxModelRetries()).isEqualTo(2);
    }

    @Test
    void preservesNullSystemPromptWhenNoResourcesAreDiscovered() throws Exception {
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
                Map.of(),
                1);

        PreparedAgentLoopRequest prepared = new CodingAgentLoopRequestFactory().prepare(request, home);

        assertThat(prepared.request().systemPrompt()).isNull();
        assertThat(prepared.discovery().contextFiles()).isEmpty();
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
