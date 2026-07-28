package com.agent4j.coding.tool;

import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolResult;
import com.agent4j.core.operation.FileSystemEntry;
import com.agent4j.core.operation.FileSystemOps;
import com.agent4j.core.operation.ProcessOps;
import com.agent4j.core.operation.ProcessResult;
import com.agent4j.core.runtime.AbortController;
import com.agent4j.core.tool.ToolContext;
import com.agent4j.core.tool.ToolExecutor;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CodingToolsTest {
    private static final Path CWD = Path.of("/repo");
    private final FakeFileSystemOps files = new FakeFileSystemOps();
    private final FakeProcessOps processes = new FakeProcessOps();
    private final CodingTools tools = new CodingTools(
            files,
            processes,
            new WorkspacePathPolicy(),
            new CodingToolLimits(8, 2, Duration.ofSeconds(5)));
    private final ToolExecutor executor = new ToolExecutor(tools.registry());

    @Test
    void exposesFirstParityToolSpecsInStableOrder() {
        assertThat(tools.registry().specs()).extracting(com.agent4j.core.tool.ToolSpec::name)
                .containsExactly("read", "write", "edit", "bash", "ls", "grep", "find");
    }

    @Test
    void readsFileAndReportsTruncation() {
        files.writeStringUnchecked(CWD.resolve("README.md"), "hello world");

        ToolResult result = execute("read", args().put("path", "README.md"));

        assertThat(result.error()).isFalse();
        assertThat(result.content().get("content").asText()).isEqualTo("hello wo");
        assertThat(result.content().get("truncated").asBoolean()).isTrue();
        assertThat(result.content().get("originalLength").asInt()).isEqualTo(11);
    }

    @Test
    void readDecodesFilesContainingNulBytes() {
        files.writeBytesUnchecked(CWD.resolve("image.bin"), new byte[]{1, 2, 0, 3});

        ToolResult result = execute("read", args().put("path", "image.bin"));

        assertThat(result.error()).isFalse();
        assertThat(result.content().get("content").asText()).isEqualTo("\u0001\u0002\u0000\u0003");
    }

    @Test
    void writeCreatesFileContent() {
        ToolResult result = execute("write", args()
                .put("path", "src/Main.java")
                .put("content", "class Main {}"));

        assertThat(result.error()).isFalse();
        assertThat(files.readStringUnchecked(CWD.resolve("src/Main.java"))).isEqualTo("class Main {}");
        assertThat(result.content().get("bytesWritten").asInt()).isEqualTo("class Main {}".getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }

    @Test
    void editReplacesFirstExactOccurrence() {
        files.writeStringUnchecked(CWD.resolve("file.txt"), "one two two");

        ToolResult result = execute("edit", args()
                .put("path", "file.txt")
                .put("oldText", "two")
                .put("newText", "three"));

        assertThat(result.error()).isFalse();
        assertThat(files.readStringUnchecked(CWD.resolve("file.txt"))).isEqualTo("one three two");
        assertThat(result.content().get("replacements").asInt()).isEqualTo(1);
        assertThat(result.content().get("matchCount").asInt()).isEqualTo(2);
        assertThat(result.content().get("ambiguous").asBoolean()).isTrue();
        assertThat(result.content().get("diff").asText()).isEqualTo("- two\n+ three");
        assertThat(result.content().get("contextBefore").asText()).isEqualTo("one ");
        assertThat(result.content().get("contextAfter").asText()).isEqualTo(" two");
    }

    @Test
    void editReturnsErrorWhenOldTextMissing() {
        files.writeStringUnchecked(CWD.resolve("file.txt"), "one two");

        ToolResult result = execute("edit", args()
                .put("path", "file.txt")
                .put("oldText", "missing")
                .put("newText", "three"));

        assertThat(result.error()).isTrue();
        assertThat(result.content().asText()).contains("oldText not found");
        assertThat(files.readStringUnchecked(CWD.resolve("file.txt"))).isEqualTo("one two");
    }

    @Test
    void editDecodesFilesContainingNulBytes() {
        files.writeBytesUnchecked(CWD.resolve("image.bin"), new byte[]{1, 2, 0, 3});

        ToolResult result = execute("edit", args()
                .put("path", "image.bin")
                .put("oldText", "\u0000")
                .put("newText", "b"));

        assertThat(result.error()).isFalse();
        assertThat(files.readStringUnchecked(CWD.resolve("image.bin"))).isEqualTo("\u0001\u0002b\u0003");
    }

    @Test
    void rejectsPathEscapeThroughExecutorErrorResult() {
        ToolResult result = execute("read", args().put("path", "../secret.txt"));

        assertThat(result.error()).isTrue();
        assertThat(result.content().asText()).contains("path escapes workspace");
    }

    @Test
    void bashRunsShellCommandThroughProcessOps() {
        processes.next = new ProcessResult(7, "standard output", "standard error", Duration.ofMillis(12), false);

        ToolResult result = execute("bash", args()
                .put("command", "echo hello")
                .put("timeoutSeconds", 2));

        assertThat(result.error()).isFalse();
        assertThat(processes.command).containsExactly("/bin/sh", "-lc", "echo hello");
        assertThat(processes.cwd).isEqualTo(CWD);
        assertThat(processes.timeout).isEqualTo(Duration.ofSeconds(2));
        assertThat(result.content().get("exitCode").asInt()).isEqualTo(7);
        assertThat(result.content().get("stdout").asText()).isEqualTo("standard");
        assertThat(result.content().get("stderr").asText()).isEqualTo("standard");
        assertThat(result.content().get("stdoutTruncated").asBoolean()).isTrue();
    }

    @Test
    void bashUsesDefaultTimeout() {
        processes.next = new ProcessResult(0, "", "", Duration.ZERO, false);

        execute("bash", args().put("command", "pwd"));

        assertThat(processes.timeout).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void lsListsDirectoryEntries() {
        files.mkdir(CWD.resolve("src"));
        files.writeStringUnchecked(CWD.resolve("src/Main.java"), "class Main {}");
        files.mkdir(CWD.resolve("src/test"));

        ToolResult result = execute("ls", args().put("path", "src"));

        assertThat(result.error()).isFalse();
        assertThat(result.content().get("entries")).hasSize(2);
        assertThat(result.content().get("entries").get(0).get("path").asText()).isEqualTo("src/Main.java");
        assertThat(result.content().get("entries").get(0).get("directory").asBoolean()).isFalse();
        assertThat(result.content().get("entries").get(1).get("path").asText()).isEqualTo("src/test");
        assertThat(result.content().get("entries").get(1).get("directory").asBoolean()).isTrue();
        assertThat(result.content().get("truncated").asBoolean()).isFalse();
        assertThat(result.content().get("totalEntries").asInt()).isEqualTo(2);
    }

    @Test
    void grepSearchesFilesRecursively() {
        files.mkdir(CWD.resolve("src"));
        files.writeStringUnchecked(CWD.resolve("src/Main.java"), "alpha\nbeta target\ngamma");
        files.writeStringUnchecked(CWD.resolve("README.md"), "target root");
        files.writeBytesUnchecked(CWD.resolve("image.bin"), new byte[]{'t', 'a', 'r', 'g', 'e', 't', 0});

        ToolResult result = execute("grep", args()
                .put("path", ".")
                .put("pattern", "target"));

        assertThat(result.error()).isFalse();
        assertThat(result.content().get("matches")).hasSize(2);
        assertThat(result.content().get("matches").get(0).get("path").asText()).isEqualTo("README.md");
        assertThat(result.content().get("matches").get(0).get("line").asInt()).isEqualTo(1);
        assertThat(result.content().get("matches").get(1).get("path").asText()).isEqualTo("image.bin");
        assertThat(result.content().get("matches").get(1).get("line").asInt()).isEqualTo(1);
        assertThat(result.content().get("truncated").asBoolean()).isTrue();
        assertThat(result.content().get("totalMatches").asInt()).isEqualTo(3);
    }

    @Test
    void grepReportsInvalidPatternAsExecutorErrorResult() {
        ToolResult result = execute("grep", args().put("pattern", "["));

        assertThat(result.error()).isTrue();
        assertThat(result.content().asText()).contains("invalid grep pattern");
    }

    @Test
    void findMatchesEntriesByName() {
        files.mkdir(CWD.resolve("src"));
        files.writeStringUnchecked(CWD.resolve("src/Main.java"), "class Main {}");
        files.writeStringUnchecked(CWD.resolve("src/Other.java"), "class Other {}");
        files.writeStringUnchecked(CWD.resolve("README.md"), "readme");

        ToolResult result = execute("find", args()
                .put("path", ".")
                .put("name", ".java"));

        assertThat(result.error()).isFalse();
        assertThat(result.content().get("entries")).hasSize(2);
        assertThat(result.content().get("entries").get(0).get("path").asText()).isEqualTo("src/Main.java");
        assertThat(result.content().get("entries").get(1).get("path").asText()).isEqualTo("src/Other.java");
        assertThat(result.content().get("truncated").asBoolean()).isFalse();
        assertThat(result.content().get("totalEntries").asInt()).isEqualTo(2);
    }

    @Test
    void lsReportsResultTruncation() {
        files.mkdir(CWD.resolve("src"));
        files.writeStringUnchecked(CWD.resolve("src/A.java"), "");
        files.writeStringUnchecked(CWD.resolve("src/B.java"), "");
        files.writeStringUnchecked(CWD.resolve("src/C.java"), "");

        ToolResult result = execute("ls", args().put("path", "src"));

        assertThat(result.content().get("entries")).hasSize(2);
        assertThat(result.content().get("truncated").asBoolean()).isTrue();
        assertThat(result.content().get("totalEntries").asInt()).isEqualTo(3);
    }

    @Test
    void grepReportsResultTruncation() {
        files.writeStringUnchecked(CWD.resolve("README.md"), "target 1\ntarget 2\ntarget 3");

        ToolResult result = execute("grep", args().put("pattern", "target"));

        assertThat(result.content().get("matches")).hasSize(2);
        assertThat(result.content().get("truncated").asBoolean()).isTrue();
        assertThat(result.content().get("totalMatches").asInt()).isEqualTo(3);
    }

    @Test
    void findReportsResultTruncation() {
        files.writeStringUnchecked(CWD.resolve("A.java"), "");
        files.writeStringUnchecked(CWD.resolve("B.java"), "");
        files.writeStringUnchecked(CWD.resolve("C.java"), "");

        ToolResult result = execute("find", args().put("name", ".java"));

        assertThat(result.content().get("entries")).hasSize(2);
        assertThat(result.content().get("truncated").asBoolean()).isTrue();
        assertThat(result.content().get("totalEntries").asInt()).isEqualTo(3);
    }

    private ToolResult execute(String toolName, com.fasterxml.jackson.databind.node.ObjectNode arguments) {
        return executor.execute(new ToolCall("call-1", toolName, arguments), context());
    }

    private ToolContext context() {
        return new ToolContext("session-1", CWD, Clock.systemUTC(), new AbortController().signal(), Map.of());
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode args() {
        return JsonNodeFactory.instance.objectNode();
    }

    private static final class FakeFileSystemOps implements FileSystemOps {
        private final Map<Path, String> files = new HashMap<>();
        private final Map<Path, byte[]> byteFiles = new HashMap<>();
        private final Map<Path, Boolean> directories = new HashMap<>(Map.of(CWD, true));

        @Override
        public byte[] readBytes(Path path) throws IOException {
            if (!byteFiles.containsKey(path)) {
                throw new IOException("missing file: " + path);
            }
            return byteFiles.get(path);
        }

        @Override
        public String readString(Path path) throws IOException {
            if (!files.containsKey(path)) {
                throw new IOException("missing file: " + path);
            }
            return files.get(path);
        }

        @Override
        public void writeString(Path path, String content) {
            mkdir(path.getParent());
            files.put(path, content);
            byteFiles.put(path, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public boolean exists(Path path) {
            return files.containsKey(path) || byteFiles.containsKey(path) || directories.containsKey(path);
        }

        @Override
        public boolean isDirectory(Path path) {
            return directories.containsKey(path);
        }

        @Override
        public List<FileSystemEntry> list(Path path) {
            return entriesUnder(path, false);
        }

        @Override
        public List<FileSystemEntry> walk(Path path) {
            return entriesUnder(path, true);
        }

        private void writeStringUnchecked(Path path, String content) {
            writeString(path, content);
        }

        private String readStringUnchecked(Path path) {
            return files.get(path);
        }

        private void mkdir(Path path) {
            if (path == null) {
                return;
            }
            Path current = path.isAbsolute() ? path.getRoot() : null;
            for (Path part : path) {
                current = current == null ? part : current.resolve(part);
                directories.put(current, true);
            }
            directories.put(path, true);
        }

        private List<FileSystemEntry> entriesUnder(Path root, boolean recursive) {
            return java.util.stream.Stream.concat(
                            byteFiles.keySet().stream().map(path -> new FileSystemEntry(path, false)),
                            directories.keySet().stream()
                                    .filter(path -> !path.equals(root))
                                    .map(path -> new FileSystemEntry(path, true)))
                    .filter(entry -> recursive ? entry.path().startsWith(root) : isDirectChild(root, entry.path()))
                    .sorted(java.util.Comparator.comparing(entry -> entry.path().toString()))
                    .toList();
        }

        private boolean isDirectChild(Path root, Path child) {
            return child.getParent() != null && child.getParent().equals(root);
        }

        private void writeBytesUnchecked(Path path, byte[] bytes) {
            mkdir(path.getParent());
            byteFiles.put(path, bytes);
        }
    }

    private static final class FakeProcessOps implements ProcessOps {
        private ProcessResult next = new ProcessResult(0, "", "", Duration.ZERO, false);
        private List<String> command;
        private Path cwd;
        private Duration timeout;

        @Override
        public ProcessResult run(List<String> command, Path cwd, Duration timeout) {
            this.command = List.copyOf(command);
            this.cwd = cwd;
            this.timeout = timeout;
            return next;
        }
    }
}
