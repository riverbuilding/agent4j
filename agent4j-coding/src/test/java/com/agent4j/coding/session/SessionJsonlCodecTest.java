package com.agent4j.coding.session;

import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionJsonlCodecTest {
    private final SessionJsonlCodec codec = new SessionJsonlCodec();

    @Test
    void readsPiShapedSessionJsonl() throws Exception {
        String jsonl = """
                {"type":"session","version":3,"id":"session-1","timestamp":"2026-07-28T10:00:00Z","cwd":"/repo"}
                {"type":"message","id":"a1b2c3d4","parentId":null,"timestamp":"2026-07-28T10:00:01Z","message":{"role":"user","content":"Hello"}}
                {"type":"model_change","id":"b2c3d4e5","parentId":"a1b2c3d4","timestamp":"2026-07-28T10:00:02Z","provider":"openai","modelId":"gpt-5"}
                {"type":"thinking_level_change","id":"c3d4e5f6","parentId":"b2c3d4e5","timestamp":"2026-07-28T10:00:03Z","thinkingLevel":"high"}
                """;

        SessionDocument document = codec.read(new StringReader(jsonl));

        assertThat(document.header().type()).isEqualTo(SessionEntryType.SESSION);
        assertThat(document.header().timestamp()).isEqualTo(Instant.parse("2026-07-28T10:00:00Z"));
        assertThat(document.entries()).hasSize(3);
        assertThat(document.entries().get(0).type()).isEqualTo(SessionEntryType.MESSAGE);
        assertThat(document.entries().get(0).payload().at("/message/role").asText()).isEqualTo("user");
    }

    @Test
    void writesOriginalPayloadSoUnknownFieldsRoundTrip() throws Exception {
        String jsonl = """
                {"type":"session","version":3,"id":"session-1","timestamp":"2026-07-28T10:00:00Z","cwd":"/repo","futureField":true}
                {"type":"custom","id":"a1b2c3d4","parentId":null,"timestamp":"2026-07-28T10:00:01Z","customType":"vendor","nested":{"kept":true}}
                """;
        SessionDocument document = codec.read(new StringReader(jsonl));
        StringWriter writer = new StringWriter();

        codec.write(document, writer);

        String written = writer.toString();
        assertThat(written).contains("\"futureField\":true");
        assertThat(written).contains("\"nested\":{\"kept\":true}");
    }

    @Test
    void buildsActivePathThroughBranchParents() throws Exception {
        SessionDocument document = readFixture("pi-sessions/branched-session.jsonl");

        SessionTree tree = SessionTree.from(document);

        assertThat(tree.childrenOf("root0001")).extracting(SessionEntry::id).containsExactly("left0001", "right001");
        assertThat(tree.activePathTo("leaf0001")).extracting(SessionEntry::id)
                .containsExactly("root0001", "right001", "leaf0001");
    }

    @Test
    void rejectsEmptySession() {
        assertThatThrownBy(() -> codec.read(new StringReader("")))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsDuplicateTreeIds() throws Exception {
        String jsonl = """
                {"type":"session","version":3,"id":"session-1","timestamp":"2026-07-28T10:00:00Z","cwd":"/repo"}
                {"type":"message","id":"same0001","parentId":null,"timestamp":"2026-07-28T10:00:01Z","message":{"role":"user","content":"one"}}
                {"type":"message","id":"same0001","parentId":null,"timestamp":"2026-07-28T10:00:02Z","message":{"role":"user","content":"two"}}
                """;
        SessionDocument document = codec.read(new StringReader(jsonl));

        assertThatThrownBy(() -> SessionTree.from(document))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void rejectsMissingTreeEntryId() throws Exception {
        String jsonl = """
                {"type":"session","version":3,"id":"session-1","timestamp":"2026-07-28T10:00:00Z","cwd":"/repo"}
                {"type":"message","parentId":null,"timestamp":"2026-07-28T10:00:01Z","message":{"role":"user","content":"one"}}
                """;
        SessionDocument document = codec.read(new StringReader(jsonl));

        assertThatThrownBy(() -> SessionTree.from(document))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing id");
    }

    @Test
    void rejectsUnknownParentReference() throws Exception {
        String jsonl = """
                {"type":"session","version":3,"id":"session-1","timestamp":"2026-07-28T10:00:00Z","cwd":"/repo"}
                {"type":"message","id":"child001","parentId":"missing1","timestamp":"2026-07-28T10:00:01Z","message":{"role":"user","content":"one"}}
                """;
        SessionDocument document = codec.read(new StringReader(jsonl));

        assertThatThrownBy(() -> SessionTree.from(document))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("child001")
                .hasMessageContaining("unknown parentId")
                .hasMessageContaining("missing1");
    }

    @Test
    void rejectsFutureParentReference() throws Exception {
        String jsonl = """
                {"type":"session","version":3,"id":"session-1","timestamp":"2026-07-28T10:00:00Z","cwd":"/repo"}
                {"type":"message","id":"child001","parentId":"future01","timestamp":"2026-07-28T10:00:01Z","message":{"role":"user","content":"one"}}
                {"type":"message","id":"future01","parentId":null,"timestamp":"2026-07-28T10:00:02Z","message":{"role":"assistant","content":[{"type":"text","text":"later"}]}}
                """;
        SessionDocument document = codec.read(new StringReader(jsonl));

        assertThatThrownBy(() -> SessionTree.from(document))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown parentId")
                .hasMessageContaining("future01");
    }

    @Test
    void rejectsSelfParentReference() throws Exception {
        String jsonl = """
                {"type":"session","version":3,"id":"session-1","timestamp":"2026-07-28T10:00:00Z","cwd":"/repo"}
                {"type":"message","id":"self0001","parentId":"self0001","timestamp":"2026-07-28T10:00:01Z","message":{"role":"user","content":"one"}}
                """;
        SessionDocument document = codec.read(new StringReader(jsonl));

        assertThatThrownBy(() -> SessionTree.from(document))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("own parent")
                .hasMessageContaining("self0001");
    }

    @Test
    void exposesTypedMessageRoleViewWithoutLosingPayload() throws Exception {
        SessionDocument document = readFixture("pi-sessions/branched-session.jsonl");

        assertThat(document.entries().getFirst().messageRole()).contains(SessionMessageRole.USER);
        assertThat(document.entries().get(1).messageRole()).contains(SessionMessageRole.ASSISTANT);
        assertThat(document.entries().get(1).message().orElseThrow().content().get(0).get("text").asText())
                .isEqualTo("left");
    }

    @Test
    void exposesTypedViewsForAllKnownEntryTypes() throws Exception {
        SessionDocument document = readFixture("pi-sessions/all-entry-types.jsonl");

        assertThat(document.header().header().orElseThrow().version()).isEqualTo(3);
        assertThat(document.entries()).extracting(SessionEntry::type).contains(
                SessionEntryType.MESSAGE,
                SessionEntryType.MODEL_CHANGE,
                SessionEntryType.THINKING_LEVEL_CHANGE,
                SessionEntryType.COMPACTION,
                SessionEntryType.SESSION_INFO,
                SessionEntryType.FILE,
                SessionEntryType.CUSTOM);
        assertThat(document.entries()).extracting(entry -> entry.messageRole().orElse(null)).contains(
                SessionMessageRole.USER,
                SessionMessageRole.ASSISTANT,
                SessionMessageRole.TOOL_RESULT,
                SessionMessageRole.BASH_EXECUTION,
                SessionMessageRole.CUSTOM,
                SessionMessageRole.BRANCH_SUMMARY,
                SessionMessageRole.COMPACTION_SUMMARY);
        assertThat(document.entries().get(7).modelChange().orElseThrow().modelId()).isEqualTo("gpt-5");
        assertThat(document.entries().get(8).thinkingLevelChange().orElseThrow().thinkingLevel()).isEqualTo("high");
        assertThat(document.entries().get(9).compaction().orElseThrow().optionalSummary()).isPresent();
        assertThat(document.entries().get(10).sessionInfo().orElseThrow().optionalName()).contains("work session");
        assertThat(document.entries().get(11).fileEntry().orElseThrow().optionalPath()).contains("src/Main.java");
        assertThat(document.entries().get(12).customEntry().orElseThrow().optionalCustomType()).contains("vendor");
    }

    private SessionDocument readFixture(String name) throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStreamReader reader = new InputStreamReader(
                classLoader.getResourceAsStream(name),
                StandardCharsets.UTF_8)) {
            return codec.read(reader);
        }
    }
}
