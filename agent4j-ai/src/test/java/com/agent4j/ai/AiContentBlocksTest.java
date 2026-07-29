package com.agent4j.ai;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiContentBlocksTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @Test
    void serializesAndParsesPiStyleContentBlocks() {
        List<AiContentBlock> blocks = List.of(
                new AiTextContent("hello"),
                new AiThinkingContent("considering", "sig-1", false),
                new AiImageContent("base64", "image/png"),
                new AiToolCallContent("call-1", "echo", JSON.objectNode().put("text", "hello"), "thought-1"));

        var json = AiContentBlocks.toJsonArray(blocks);
        List<AiContentBlock> parsed = AiContentBlocks.parse(json);

        assertThat(json.get(0).get("type").asText()).isEqualTo("text");
        assertThat(json.get(1).get("type").asText()).isEqualTo("thinking");
        assertThat(json.get(2).get("mimeType").asText()).isEqualTo("image/png");
        assertThat(json.get(3).get("type").asText()).isEqualTo("toolCall");
        assertThat(parsed).isEqualTo(blocks);
    }
}
