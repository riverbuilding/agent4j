package com.agent4j.coding.tool;

import com.agent4j.core.message.ToolResult;
import com.agent4j.core.operation.FileSystemEntry;
import com.agent4j.core.operation.FileSystemOps;
import com.agent4j.core.operation.PathPolicy;
import com.agent4j.core.operation.ProcessOps;
import com.agent4j.core.operation.ProcessResult;
import com.agent4j.core.tool.InMemoryToolRegistry;
import com.agent4j.core.tool.ToolRegistry;
import com.agent4j.core.tool.ToolSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class CodingTools {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final FileSystemOps fileSystemOps;
    private final ProcessOps processOps;
    private final PathPolicy pathPolicy;
    private final CodingToolLimits limits;

    public CodingTools(FileSystemOps fileSystemOps, ProcessOps processOps, PathPolicy pathPolicy, CodingToolLimits limits) {
        this.fileSystemOps = Objects.requireNonNull(fileSystemOps, "fileSystemOps");
        this.processOps = Objects.requireNonNull(processOps, "processOps");
        this.pathPolicy = Objects.requireNonNull(pathPolicy, "pathPolicy");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public static CodingTools localDefaults() {
        return new CodingTools(new LocalFileSystemOps(), new LocalProcessOps(), new WorkspacePathPolicy(), CodingToolLimits.defaults());
    }

    public ToolRegistry registry() {
        return InMemoryToolRegistry.builder()
                .register(readSpec(), this::read)
                .register(writeSpec(), this::write)
                .register(editSpec(), this::edit)
                .register(bashSpec(), this::bash)
                .register(lsSpec(), this::ls)
                .register(grepSpec(), this::grep)
                .register(findSpec(), this::find)
                .build();
    }

    private ToolResult read(com.agent4j.core.message.ToolCall call, com.agent4j.core.tool.ToolContext context) throws Exception {
        Path path = resolvePath(context, requiredText(call.arguments(), "path"));
        if (!fileSystemOps.exists(path)) {
            return error(call, "file does not exist: " + path);
        }
        if (fileSystemOps.isDirectory(path)) {
            return error(call, "path is a directory: " + path);
        }
        String text = readTextFile(path);
        TruncatedText content = TruncatedText.of(text, limits.maxOutputChars());
        ObjectNode result = JSON.objectNode();
        result.put("path", displayPath(context, path));
        result.put("content", content.text());
        result.put("truncated", content.truncated());
        result.put("originalLength", content.originalLength());
        return ok(call, result);
    }

    private ToolResult write(com.agent4j.core.message.ToolCall call, com.agent4j.core.tool.ToolContext context) throws Exception {
        Path path = resolvePath(context, requiredText(call.arguments(), "path"));
        String content = requiredText(call.arguments(), "content");
        fileSystemOps.writeString(path, content);
        ObjectNode result = JSON.objectNode();
        result.put("path", displayPath(context, path));
        result.put("bytesWritten", content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        return ok(call, result);
    }

    private ToolResult edit(com.agent4j.core.message.ToolCall call, com.agent4j.core.tool.ToolContext context) throws Exception {
        Path path = resolvePath(context, requiredText(call.arguments(), "path"));
        String oldText = requiredText(call.arguments(), "oldText");
        String newText = requiredText(call.arguments(), "newText");
        if (!fileSystemOps.exists(path)) {
            return error(call, "file does not exist: " + path);
        }
        String original = readTextFile(path);
        int first = original.indexOf(oldText);
        if (first < 0) {
            return error(call, "oldText not found in file: " + path);
        }
        int matchCount = countOccurrences(original, oldText);
        String updated = original.substring(0, first) + newText + original.substring(first + oldText.length());
        fileSystemOps.writeString(path, updated);
        ObjectNode result = JSON.objectNode();
        result.put("path", displayPath(context, path));
        result.put("replacements", 1);
        result.put("matchCount", matchCount);
        result.put("ambiguous", matchCount > 1);
        result.put("oldText", oldText);
        result.put("newText", newText);
        result.put("diff", diff(oldText, newText));
        result.put("contextBefore", contextBefore(original, first));
        result.put("contextAfter", contextAfter(original, first + oldText.length()));
        return ok(call, result);
    }

    private ToolResult bash(com.agent4j.core.message.ToolCall call, com.agent4j.core.tool.ToolContext context) throws Exception {
        String command = requiredText(call.arguments(), "command");
        Duration timeout = timeout(call.arguments());
        ProcessResult processResult = processOps.run(List.of("/bin/sh", "-lc", command), context.cwd(), timeout);
        TruncatedText stdout = TruncatedText.of(processResult.stdout(), limits.maxOutputChars());
        TruncatedText stderr = TruncatedText.of(processResult.stderr(), limits.maxOutputChars());
        ObjectNode result = JSON.objectNode();
        result.put("command", command);
        result.put("exitCode", processResult.exitCode());
        result.put("stdout", stdout.text());
        result.put("stderr", stderr.text());
        result.put("stdoutTruncated", stdout.truncated());
        result.put("stderrTruncated", stderr.truncated());
        result.put("timedOut", processResult.timedOut());
        result.put("durationMillis", processResult.duration().toMillis());
        return ok(call, result);
    }

    private ToolResult ls(com.agent4j.core.message.ToolCall call, com.agent4j.core.tool.ToolContext context) throws Exception {
        Path path = resolvePath(context, optionalText(call.arguments(), "path", "."));
        if (!fileSystemOps.exists(path)) {
            return error(call, "path does not exist: " + path);
        }
        if (!fileSystemOps.isDirectory(path)) {
            return error(call, "path is not a directory: " + path);
        }
        var entries = JSON.arrayNode();
        List<FileSystemEntry> listed = fileSystemOps.list(path);
        int emitted = 0;
        for (FileSystemEntry entry : listed) {
            if (emitted >= limits.maxResultItems()) {
                break;
            }
            ObjectNode item = JSON.objectNode();
            item.put("path", context.cwd().relativize(entry.path()).toString());
            item.put("directory", entry.directory());
            entries.add(item);
            emitted++;
        }
        ObjectNode result = JSON.objectNode();
        result.put("path", displayPath(context, path));
        result.set("entries", entries);
        result.put("truncated", listed.size() > emitted);
        result.put("totalEntries", listed.size());
        return ok(call, result);
    }

    private ToolResult grep(com.agent4j.core.message.ToolCall call, com.agent4j.core.tool.ToolContext context) throws Exception {
        String pattern = requiredText(call.arguments(), "pattern");
        Pattern compiled = compilePattern(pattern);
        Path path = resolvePath(context, optionalText(call.arguments(), "path", "."));
        if (!fileSystemOps.exists(path)) {
            return error(call, "path does not exist: " + path);
        }
        List<FileSystemEntry> candidates = fileSystemOps.isDirectory(path)
                ? fileSystemOps.walk(path).stream().filter(entry -> !entry.directory()).toList()
                : List.of(new FileSystemEntry(path, false));
        var matches = JSON.arrayNode();
        int totalMatches = 0;
        for (FileSystemEntry candidate : candidates) {
            String content = readTextFile(candidate.path());
            String[] lines = content.split("\\R", -1);
            for (int i = 0; i < lines.length; i++) {
                if (compiled.matcher(lines[i]).find()) {
                    totalMatches++;
                    if (matches.size() >= limits.maxResultItems()) {
                        continue;
                    }
                    ObjectNode match = JSON.objectNode();
                    match.put("path", context.cwd().relativize(candidate.path()).toString());
                    match.put("line", i + 1);
                    match.put("text", TruncatedText.of(lines[i], limits.maxOutputChars()).text());
                    matches.add(match);
                }
            }
        }
        ObjectNode result = JSON.objectNode();
        result.put("pattern", pattern);
        result.put("path", displayPath(context, path));
        result.set("matches", matches);
        result.put("truncated", totalMatches > matches.size());
        result.put("totalMatches", totalMatches);
        return ok(call, result);
    }

    private ToolResult find(com.agent4j.core.message.ToolCall call, com.agent4j.core.tool.ToolContext context) throws Exception {
        String name = optionalText(call.arguments(), "name", "");
        Path path = resolvePath(context, optionalText(call.arguments(), "path", "."));
        if (!fileSystemOps.exists(path)) {
            return error(call, "path does not exist: " + path);
        }
        List<FileSystemEntry> candidates = fileSystemOps.isDirectory(path)
                ? fileSystemOps.walk(path)
                : List.of(new FileSystemEntry(path, false));
        var entries = JSON.arrayNode();
        int totalMatches = 0;
        for (FileSystemEntry candidate : candidates) {
            String fileName = candidate.path().getFileName() == null ? "" : candidate.path().getFileName().toString();
            if (!name.isBlank() && !fileName.contains(name)) {
                continue;
            }
            totalMatches++;
            if (entries.size() >= limits.maxResultItems()) {
                continue;
            }
            ObjectNode item = JSON.objectNode();
            item.put("path", context.cwd().relativize(candidate.path()).toString());
            item.put("directory", candidate.directory());
            entries.add(item);
        }
        ObjectNode result = JSON.objectNode();
        result.put("path", displayPath(context, path));
        result.put("name", name);
        result.set("entries", entries);
        result.put("truncated", totalMatches > entries.size());
        result.put("totalEntries", totalMatches);
        return ok(call, result);
    }

    private Path resolvePath(com.agent4j.core.tool.ToolContext context, String path) {
        return pathPolicy.resolve(context.cwd(), Path.of(path));
    }

    private static String displayPath(com.agent4j.core.tool.ToolContext context, Path path) {
        Path relative = context.cwd().relativize(path);
        return relative.toString().isBlank() ? "." : relative.toString();
    }

    private String readTextFile(Path path) throws Exception {
        byte[] bytes = fileSystemOps.readBytes(path);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private Duration timeout(JsonNode arguments) {
        JsonNode timeoutSeconds = arguments == null ? null : arguments.get("timeoutSeconds");
        if (timeoutSeconds == null || !timeoutSeconds.canConvertToLong()) {
            return limits.defaultCommandTimeout();
        }
        long seconds = timeoutSeconds.asLong();
        if (seconds < 1) {
            throw new IllegalArgumentException("timeoutSeconds must be positive");
        }
        return Duration.ofSeconds(seconds);
    }

    private static String requiredText(JsonNode arguments, String fieldName) {
        JsonNode value = arguments == null ? null : arguments.get(fieldName);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException("missing textual argument: " + fieldName);
        }
        return value.asText();
    }

    private static String optionalText(JsonNode arguments, String fieldName, String defaultValue) {
        JsonNode value = arguments == null ? null : arguments.get(fieldName);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException("argument must be textual: " + fieldName);
        }
        return value.asText();
    }

    private static Pattern compilePattern(String pattern) {
        try {
            return Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("invalid grep pattern: " + e.getMessage(), e);
        }
    }

    private static String diff(String oldText, String newText) {
        return "- " + oldText.replace("\n", "\n- ") + "\n+ " + newText.replace("\n", "\n+ ");
    }

    private static int countOccurrences(String text, String needle) {
        if (needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static String contextBefore(String text, int index) {
        int start = Math.max(0, index - 80);
        return text.substring(start, index);
    }

    private static String contextAfter(String text, int index) {
        int end = Math.min(text.length(), index + 80);
        return text.substring(index, end);
    }

    private static ToolResult ok(com.agent4j.core.message.ToolCall call, JsonNode content) {
        return new ToolResult(call.id(), call.name(), false, content, JSON.objectNode());
    }

    private static ToolResult error(com.agent4j.core.message.ToolCall call, String message) {
        ObjectNode metadata = JSON.objectNode();
        metadata.put("message", message);
        return new ToolResult(call.id(), call.name(), true, JSON.textNode(message), metadata);
    }

    private static ToolSpec readSpec() {
        return new ToolSpec("read", "Read a UTF-8 text file from the workspace.", schema(
                new Field("path", "string", "Workspace-relative file path to read.", true)));
    }

    private static ToolSpec writeSpec() {
        return new ToolSpec("write", "Write UTF-8 text content to a workspace file.", schema(
                new Field("path", "string", "Workspace-relative file path to write.", true),
                new Field("content", "string", "Complete file content to write.", true)));
    }

    private static ToolSpec editSpec() {
        return new ToolSpec("edit", "Replace the first exact text occurrence in a workspace file.", schema(
                new Field("path", "string", "Workspace-relative file path to edit.", true),
                new Field("oldText", "string", "Exact text to replace.", true),
                new Field("newText", "string", "Replacement text.", true)));
    }

    private static ToolSpec bashSpec() {
        return new ToolSpec("bash", "Run a shell command in the workspace.", schema(
                new Field("command", "string", "Shell command to run.", true),
                new Field("timeoutSeconds", "integer", "Optional command timeout in seconds.", false)));
    }

    private static ToolSpec lsSpec() {
        return new ToolSpec("ls", "List files in a workspace directory.", schema(
                new Field("path", "string", "Workspace-relative directory path to list. Defaults to the workspace root.", false)));
    }

    private static ToolSpec grepSpec() {
        return new ToolSpec("grep", "Search workspace text files with a regular expression.", schema(
                new Field("pattern", "string", "Java regular expression to search for.", true),
                new Field("path", "string", "Workspace-relative file or directory path to search. Defaults to the workspace root.", false)));
    }

    private static ToolSpec findSpec() {
        return new ToolSpec("find", "Find files or directories by name within the workspace.", schema(
                new Field("path", "string", "Workspace-relative file or directory path to search. Defaults to the workspace root.", false),
                new Field("name", "string", "Optional substring to match against file or directory names.", false)));
    }

    private static ObjectNode schema(Field... fields) {
        ObjectNode schema = JSON.objectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode properties = JSON.objectNode();
        var required = JSON.arrayNode();
        for (Field field : fields) {
            ObjectNode property = JSON.objectNode()
                    .put("type", field.type())
                    .put("description", field.description());
            if ("integer".equals(field.type())) {
                property.put("minimum", 1);
            }
            properties.set(field.name(), property);
            if (field.required()) {
                required.add(field.name());
            }
        }
        schema.set("properties", properties);
        schema.set("required", required);
        return schema;
    }

    private record Field(String name, String type, String description, boolean required) {
    }
}
