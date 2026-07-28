package com.agent4j.coding.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SessionManagerTest {
    @TempDir
    Path tempDir;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-28T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void createsSessionAndAppendsEntriesWithActiveCursor() throws Exception {
        Path sessionFile = tempDir.resolve("session.jsonl");
        AtomicInteger ids = new AtomicInteger();
        SessionManager manager = SessionManager.create(
                sessionFile,
                tempDir,
                new SessionJsonlCodec(),
                () -> "id%06d".formatted(ids.incrementAndGet()),
                clock);

        SessionEntry first = manager.append(SessionEntryType.MESSAGE, payload -> {
            payload.set("message", payload.objectNode()
                    .put("role", "user")
                    .put("content", "hello"));
        });
        SessionEntry second = manager.append(SessionEntryType.MODEL_CHANGE, payload -> {
            payload.put("provider", "openai");
            payload.put("modelId", "gpt-5");
        });

        assertThat(first.parentId()).isNull();
        assertThat(second.parentId()).isEqualTo(first.id());
        assertThat(manager.activeEntryId()).isEqualTo(second.id());
        assertThat(Files.readAllLines(sessionFile)).hasSize(3);

        SessionManager reopened = SessionManager.open(
                sessionFile,
                new SessionJsonlCodec(),
                () -> "unused",
                clock);
        assertThat(reopened.activePath()).extracting(SessionEntry::id)
                .containsExactly(first.id(), second.id());
    }

    @Test
    void appendsCommonEntryTypesWithTypedHelpers() throws Exception {
        Path sessionFile = tempDir.resolve("helpers.jsonl");
        AtomicInteger ids = new AtomicInteger();
        SessionManager manager = SessionManager.create(
                sessionFile,
                tempDir,
                new SessionJsonlCodec(),
                () -> "id%06d".formatted(ids.incrementAndGet()),
                clock);

        SessionEntry user = manager.appendUserMessage("hello");
        SessionEntry assistant = manager.appendAssistantText("hi");
        SessionEntry model = manager.appendModelChange("openai", "gpt-5");
        SessionEntry thinking = manager.appendThinkingLevelChange("high");
        SessionEntry info = manager.appendSessionInfo("named session");
        SessionEntry file = manager.appendFileEntry("README.md", payload -> payload.put("state", "attached"));
        SessionEntry custom = manager.appendCustomEntry("vendor", payload -> payload.put("ok", true));

        assertThat(user.message().orElseThrow().content().asText()).isEqualTo("hello");
        assertThat(assistant.message().orElseThrow().content().get(0).get("text").asText()).isEqualTo("hi");
        assertThat(model.modelChange().orElseThrow().provider()).isEqualTo("openai");
        assertThat(thinking.thinkingLevelChange().orElseThrow().thinkingLevel()).isEqualTo("high");
        assertThat(info.sessionInfo().orElseThrow().optionalName()).contains("named session");
        assertThat(file.fileEntry().orElseThrow().optionalPath()).contains("README.md");
        assertThat(custom.customEntry().orElseThrow().optionalCustomType()).contains("vendor");
        assertThat(manager.activePath()).extracting(SessionEntry::id)
                .containsExactly("id000001", "id000002", "id000003", "id000004", "id000005", "id000006", "id000007");
    }

    @Test
    void appendsNewEntryAtNavigatedBranchWithoutRewritingHistory() throws Exception {
        Path sessionFile = tempDir.resolve("session.jsonl");
        Files.writeString(sessionFile, """
                {"type":"session","version":3,"id":"session-1","timestamp":"2026-07-28T10:00:00Z","cwd":"/repo"}
                {"type":"message","id":"root0001","parentId":null,"timestamp":"2026-07-28T10:00:01Z","message":{"role":"user","content":"root"}}
                {"type":"message","id":"left0001","parentId":"root0001","timestamp":"2026-07-28T10:00:02Z","message":{"role":"assistant","content":[{"type":"text","text":"left"}]}}
                {"type":"message","id":"right001","parentId":"root0001","timestamp":"2026-07-28T10:00:03Z","message":{"role":"assistant","content":[{"type":"text","text":"right"}]}}
                """);
        SessionManager manager = SessionManager.open(
                sessionFile,
                new SessionJsonlCodec(),
                () -> "newleaf1",
                clock);

        manager.navigateTo("left0001");
        SessionEntry newLeaf = manager.append(SessionEntryType.MESSAGE, payload -> {
            payload.set("message", payload.objectNode()
                    .put("role", "user")
                    .put("content", "continue left"));
        });

        assertThat(newLeaf.parentId()).isEqualTo("left0001");
        assertThat(manager.activePath()).extracting(SessionEntry::id)
                .containsExactly("root0001", "left0001", "newleaf1");

        List<String> lines = Files.readAllLines(sessionFile);
        assertThat(lines).hasSize(5);
        assertThat(lines.get(3)).contains("\"id\":\"right001\"");
        assertThat(lines.get(4)).contains("\"id\":\"newleaf1\"");
        assertThat(lines.get(4)).contains("\"parentId\":\"left0001\"");
    }

    @Test
    void clonesWholeSessionDocumentToNewFile() throws Exception {
        Path sessionFile = tempDir.resolve("source.jsonl");
        Files.writeString(sessionFile, """
                {"type":"session","version":3,"id":"session-1","timestamp":"2026-07-28T10:00:00Z","cwd":"/repo"}
                {"type":"message","id":"root0001","parentId":null,"timestamp":"2026-07-28T10:00:01Z","message":{"role":"user","content":"root"}}
                {"type":"message","id":"left0001","parentId":"root0001","timestamp":"2026-07-28T10:00:02Z","message":{"role":"assistant","content":[{"type":"text","text":"left"}]}}
                {"type":"message","id":"right001","parentId":"root0001","timestamp":"2026-07-28T10:00:03Z","message":{"role":"assistant","content":[{"type":"text","text":"right"}]}}
                """);
        SessionManager manager = SessionManager.open(
                sessionFile,
                new SessionJsonlCodec(),
                () -> "unused",
                clock);
        Path cloneFile = tempDir.resolve("clone.jsonl");

        SessionManager clone = manager.cloneTo(cloneFile);

        assertThat(Files.readString(cloneFile)).isEqualTo(Files.readString(sessionFile));
        assertThat(clone.activeEntryId()).isEqualTo("right001");
    }

    @Test
    void forksOnlyActivePathToNewFileWithDerivedHeader() throws Exception {
        Path sessionFile = tempDir.resolve("source-fork.jsonl");
        Files.writeString(sessionFile, """
                {"type":"session","version":3,"id":"session-1","timestamp":"2026-07-28T10:00:00Z","cwd":"/repo"}
                {"type":"message","id":"root0001","parentId":null,"timestamp":"2026-07-28T10:00:01Z","message":{"role":"user","content":"root"}}
                {"type":"message","id":"left0001","parentId":"root0001","timestamp":"2026-07-28T10:00:02Z","message":{"role":"assistant","content":[{"type":"text","text":"left"}]}}
                {"type":"message","id":"right001","parentId":"root0001","timestamp":"2026-07-28T10:00:03Z","message":{"role":"assistant","content":[{"type":"text","text":"right"}]}}
                """);
        SessionManager manager = SessionManager.open(
                sessionFile,
                new SessionJsonlCodec(),
                () -> "unused",
                clock);
        manager.navigateTo("left0001");
        Path forkFile = tempDir.resolve("fork.jsonl");

        SessionManager fork = manager.forkToActivePath(forkFile);

        assertThat(fork.document().header().header().orElseThrow().sourceSessionId()).contains("session-1");
        assertThat(fork.document().header().header().orElseThrow().forkedFromEntryId()).contains("left0001");
        assertThat(fork.document().entries()).extracting(SessionEntry::id)
                .containsExactly("root0001", "left0001");
        assertThat(Files.readString(forkFile)).doesNotContain("right001");
    }

    @Test
    void importsValidatedSessionToNewFile() throws Exception {
        Path sourceFile = tempDir.resolve("import-source.jsonl");
        Files.writeString(sourceFile, """
                {"type":"session","version":3,"id":"session-1","timestamp":"2026-07-28T10:00:00Z","cwd":"/repo"}
                {"type":"message","id":"root0001","parentId":null,"timestamp":"2026-07-28T10:00:01Z","message":{"role":"user","content":"root"}}
                """);
        Path targetFile = tempDir.resolve("import-target.jsonl");

        SessionManager imported = SessionManager.importFrom(
                sourceFile,
                targetFile,
                new SessionJsonlCodec(),
                () -> "unused",
                clock);

        assertThat(Files.readString(targetFile)).isEqualTo(Files.readString(sourceFile));
        assertThat(imported.activeEntryId()).isEqualTo("root0001");
    }
}
