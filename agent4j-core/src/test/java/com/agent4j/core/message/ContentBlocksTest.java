package com.agent4j.core.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContentBlocksTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesStringContentAsSingleTextBlock() throws Exception {
        List<ContentBlock> blocks = ContentBlocks.parse(mapper.readTree("\"hello\""));

        assertThat(blocks).hasSize(1);
        assertThat(blocks.getFirst()).isInstanceOf(TextBlock.class);
        assertThat(blocks.getFirst().textValue()).contains("hello");
    }

    @Test
    void parsesKnownArrayBlocksAndPreservesUnknownRawBlock() throws Exception {
        List<ContentBlock> blocks = ContentBlocks.parse(mapper.readTree("""
                [
                  {"type":"text","text":"hello"},
                  {"type":"reasoning","text":"thinking"},
                  {"type":"toolCall","id":"tool-1","name":"read","arguments":{"path":"README.md"}},
                  {"type":"future","value":true}
                ]
                """));

        assertThat(blocks).hasSize(4);
        assertThat(blocks.get(0)).isInstanceOf(TextBlock.class);
        assertThat(blocks.get(1)).isInstanceOf(ReasoningBlock.class);
        assertThat(((ToolCallBlock) blocks.get(2)).toolCall().name()).isEqualTo("read");
        assertThat(((ToolCallBlock) blocks.get(2)).toolCall().arguments().get("path").asText()).isEqualTo("README.md");
        assertThat(((UnknownContentBlock) blocks.get(3)).rawType()).isEqualTo("future");
        assertThat(blocks.get(3).raw().get("value").asBoolean()).isTrue();
    }

    @Test
    void writesTypedBlocksToPiShapedArrayContent() {
        var content = ContentBlocks.toJsonArray(List.of(
                new TextBlock("hello", null),
                new ReasoningBlock("thinking", null),
                new ToolCallBlock(new ToolCall("tool-1", "read", mapper.createObjectNode().put("path", "README.md")), null)));

        assertThat(content.get(0).get("type").asText()).isEqualTo("text");
        assertThat(content.get(1).get("type").asText()).isEqualTo("reasoning");
        assertThat(content.get(2).get("type").asText()).isEqualTo("toolCall");
        assertThat(content.get(2).get("arguments").get("path").asText()).isEqualTo("README.md");
    }

    @Test
    void agentMessageExposesContentBlocksAndFlattenedText() {
        AgentMessage message = AgentMessage.assistantText(
                "message-1",
                null,
                Instant.parse("2026-07-28T10:00:00Z"),
                "hello",
                mapper.createObjectNode());

        assertThat(message.contentBlocks()).hasSize(1);
        assertThat(message.contentBlocks().getFirst()).isInstanceOf(TextBlock.class);
        assertThat(message.textContent()).isEqualTo("hello");
    }
}
