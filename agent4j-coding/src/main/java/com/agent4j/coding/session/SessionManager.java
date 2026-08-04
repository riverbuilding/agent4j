package com.agent4j.coding.session;

import com.agent4j.core.compaction.CompactionResult;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.agent4j.core.runtime.AgentLoopResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public final class SessionManager {
    private static final int CURRENT_SESSION_VERSION = 3;
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final Path sessionFile;
    private final SessionJsonlCodec codec;
    private final SessionIdGenerator idGenerator;
    private final Clock clock;
    private final SessionEntry header;
    private final List<SessionEntry> entries;
    private String activeEntryId;

    private SessionManager(
            Path sessionFile,
            SessionJsonlCodec codec,
            SessionIdGenerator idGenerator,
            Clock clock,
            SessionEntry header,
            List<SessionEntry> entries
    ) {
        this.sessionFile = Objects.requireNonNull(sessionFile, "sessionFile");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.header = Objects.requireNonNull(header, "header");
        this.entries = new ArrayList<>(entries);
        this.activeEntryId = this.entries.isEmpty() ? null : this.entries.getLast().id();
    }

    public static SessionManager create(Path sessionFile, Path cwd) throws IOException {
        return create(sessionFile, cwd, new SessionJsonlCodec(), SessionIdGenerator.randomHex(), Clock.systemUTC());
    }

    public static SessionManager create(
            Path sessionFile,
            Path cwd,
            SessionJsonlCodec codec,
            SessionIdGenerator idGenerator,
            Clock clock
    ) throws IOException {
        Objects.requireNonNull(cwd, "cwd");
        if (Files.exists(sessionFile)) {
            throw new IOException("session file already exists: " + sessionFile);
        }
        ObjectNode headerPayload = codec.createObjectNode();
        headerPayload.put("type", SessionEntryType.SESSION.wireName());
        headerPayload.put("version", CURRENT_SESSION_VERSION);
        headerPayload.put("id", UUID.randomUUID().toString());
        headerPayload.put("timestamp", Instant.now(clock).toString());
        headerPayload.put("cwd", cwd.toAbsolutePath().normalize().toString());

        Files.createDirectories(sessionFile.toAbsolutePath().getParent());
        SessionEntry header = codec.parseLine(codec.writeJson(headerPayload), 1);
        appendLineWithLock(sessionFile, codec.writeLine(header));
        return new SessionManager(sessionFile, codec, idGenerator, clock, header, List.of());
    }

    public static SessionManager open(Path sessionFile) throws IOException {
        return open(sessionFile, new SessionJsonlCodec(), SessionIdGenerator.randomHex(), Clock.systemUTC());
    }

    public static SessionManager open(
            Path sessionFile,
            SessionJsonlCodec codec,
            SessionIdGenerator idGenerator,
            Clock clock
    ) throws IOException {
        SessionDocument document;
        try (StringReader reader = new StringReader(Files.readString(sessionFile))) {
            document = codec.read(reader);
        }
        SessionTree.from(document);
        return new SessionManager(sessionFile, codec, idGenerator, clock, document.header(), document.entries());
    }

    public static SessionManager importFrom(Path sourceFile, Path targetFile) throws IOException {
        return importFrom(sourceFile, targetFile, new SessionJsonlCodec(), SessionIdGenerator.randomHex(), Clock.systemUTC());
    }

    public static SessionManager importFrom(
            Path sourceFile,
            Path targetFile,
            SessionJsonlCodec codec,
            SessionIdGenerator idGenerator,
            Clock clock
    ) throws IOException {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(targetFile, "targetFile");
        if (Files.exists(targetFile)) {
            throw new IOException("target session file already exists: " + targetFile);
        }
        SessionDocument document;
        try (StringReader reader = new StringReader(Files.readString(sourceFile))) {
            document = codec.read(reader);
        }
        SessionTree.from(document);
        writeDocument(targetFile, codec, document);
        return open(targetFile, codec, idGenerator, clock);
    }

    public SessionEntry append(SessionEntryType type, Consumer<ObjectNode> payloadCustomizer) throws IOException {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(payloadCustomizer, "payloadCustomizer");
        if (type == SessionEntryType.SESSION || type == SessionEntryType.UNKNOWN) {
            throw new IllegalArgumentException("cannot append entry type: " + type);
        }

        ObjectNode payload = codec.createObjectNode();
        payload.put("type", type.wireName());
        payload.put("id", idGenerator.nextId());
        if (activeEntryId == null) {
            payload.putNull("parentId");
        } else {
            payload.put("parentId", activeEntryId);
        }
        payload.put("timestamp", Instant.now(clock).toString());
        payloadCustomizer.accept(payload);

        String line = codec.writeJson(payload);
        appendFreshLineWithLock(line);
        SessionEntry entry = codec.parseLine(line, entries.size() + 2);
        entries.add(entry);
        activeEntryId = entry.id();
        return entry;
    }

    public SessionEntry appendAgentMessage(AgentMessage agentMessage) throws IOException {
        Objects.requireNonNull(agentMessage, "agentMessage");
        return appendPreservingEntryFields(
                SessionEntryType.MESSAGE,
                agentMessage.id(),
                agentMessage.timestamp(),
                payload -> payload.set("message", toSessionMessage(agentMessage)));
    }

    public List<SessionEntry> appendAgentMessages(List<AgentMessage> agentMessages) throws IOException {
        Objects.requireNonNull(agentMessages, "agentMessages");
        if (agentMessages.isEmpty()) {
            return List.of();
        }
        List<SessionEntry> appended = new ArrayList<>(agentMessages.size());
        List<String> lines = new ArrayList<>(agentMessages.size());
        String parentId = activeEntryId;
        int lineNumber = entries.size() + 2;
        for (AgentMessage agentMessage : agentMessages) {
            Objects.requireNonNull(agentMessage, "agentMessages element");
            ObjectNode payload = agentMessagePayload(agentMessage, parentId);
            String line = codec.writeJson(payload);
            lines.add(line);
            appended.add(codec.parseLine(line, lineNumber++));
            parentId = agentMessage.id();
        }
        validateAppendBatch(appended);
        appendFreshLinesWithLock(lines);
        entries.addAll(appended);
        activeEntryId = appended.getLast().id();
        return List.copyOf(appended);
    }

    public List<SessionEntry> appendAgentLoopResult(AgentLoopResult result) throws IOException {
        Objects.requireNonNull(result, "result");
        return appendAgentMessages(result.messages());
    }

    public List<SessionEntry> appendCompactionResult(CompactionResult result) throws IOException {
        Objects.requireNonNull(result, "result");
        if (!result.compacted()) {
            return List.of();
        }
        AgentMessage summaryMessage = result.summaryMessage();
        SessionEntry summaryEntry = appendAgentMessage(summaryMessage);
        SessionEntry compactionEntry = append(SessionEntryType.COMPACTION, payload -> {
            payload.set("summary", toSessionMessage(summaryMessage));
            var retainedEntries = payload.putArray("retainedEntries");
            result.retainedMessages().stream()
                    .map(AgentMessage::id)
                    .forEach(retainedEntries::add);
            payload.put("reason", result.reason().wireName());
            payload.put("summaryMessageId", summaryMessage.id());
            payload.put("usageBeforeTokens", result.usageBefore().totalTokens());
            payload.put("usageAfterTokens", result.usageAfter().totalTokens());
        });
        return List.of(summaryEntry, compactionEntry);
    }

    public SessionEntry appendMessage(SessionMessageRole role, com.fasterxml.jackson.databind.JsonNode content)
            throws IOException {
        Objects.requireNonNull(role, "role");
        return append(SessionEntryType.MESSAGE, payload -> {
            ObjectNode message = codec.createObjectNode();
            message.put("role", role.wireName());
            message.set("content", content);
            payload.set("message", message);
        });
    }

    public SessionEntry appendUserMessage(String content) throws IOException {
        return appendMessage(SessionMessageRole.USER, codec.textNode(content));
    }

    public SessionEntry appendAssistantText(String text) throws IOException {
        var content = codec.createArrayNode();
        ObjectNode textBlock = codec.createObjectNode();
        textBlock.put("type", "text");
        textBlock.put("text", text);
        content.add(textBlock);
        return appendMessage(SessionMessageRole.ASSISTANT, content);
    }

    public SessionEntry appendModelChange(String provider, String modelId) throws IOException {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(modelId, "modelId");
        return append(SessionEntryType.MODEL_CHANGE, payload -> {
            payload.put("provider", provider);
            payload.put("modelId", modelId);
        });
    }

    public SessionEntry appendThinkingLevelChange(String thinkingLevel) throws IOException {
        Objects.requireNonNull(thinkingLevel, "thinkingLevel");
        return append(SessionEntryType.THINKING_LEVEL_CHANGE, payload -> payload.put("thinkingLevel", thinkingLevel));
    }

    public SessionEntry appendSessionInfo(String name) throws IOException {
        return append(SessionEntryType.SESSION_INFO, payload -> payload.put("name", name));
    }

    public SessionEntry appendFileEntry(String path, Consumer<ObjectNode> payloadCustomizer) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(payloadCustomizer, "payloadCustomizer");
        return append(SessionEntryType.FILE, payload -> {
            payload.put("path", path);
            payloadCustomizer.accept(payload);
        });
    }

    public SessionEntry appendCustomEntry(String customType, Consumer<ObjectNode> payloadCustomizer) throws IOException {
        Objects.requireNonNull(customType, "customType");
        Objects.requireNonNull(payloadCustomizer, "payloadCustomizer");
        return append(SessionEntryType.CUSTOM, payload -> {
            payload.put("customType", customType);
            payloadCustomizer.accept(payload);
        });
    }

    public void navigateTo(String entryId) {
        Objects.requireNonNull(entryId, "entryId");
        documentTree().requireEntry(entryId);
        activeEntryId = entryId;
    }

    public SessionDocument document() {
        return new SessionDocument(header, entries);
    }

    public List<SessionEntry> activePath() {
        if (activeEntryId == null) {
            return List.of();
        }
        return documentTree().activePathTo(activeEntryId);
    }

    public List<AgentMessage> activeAgentMessages() {
        return activePath().stream()
                .map(SessionManager::toAgentMessage)
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    public Path sessionFile() {
        return sessionFile;
    }

    public String activeEntryId() {
        return activeEntryId;
    }

    public SessionManager cloneTo(Path targetFile) throws IOException {
        Objects.requireNonNull(targetFile, "targetFile");
        if (Files.exists(targetFile)) {
            throw new IOException("target session file already exists: " + targetFile);
        }
        writeDocument(targetFile, codec, document());
        return open(targetFile, codec, idGenerator, clock);
    }

    public SessionManager forkToActivePath(Path targetFile) throws IOException {
        Objects.requireNonNull(targetFile, "targetFile");
        if (Files.exists(targetFile)) {
            throw new IOException("target session file already exists: " + targetFile);
        }
        SessionEntry derivedHeader = derivedHeader();
        SessionDocument forkedDocument = new SessionDocument(derivedHeader, activePath());
        writeDocument(targetFile, codec, forkedDocument);
        return open(targetFile, codec, idGenerator, clock);
    }

    private SessionTree documentTree() {
        return SessionTree.from(document());
    }

    private SessionEntry appendPreservingEntryFields(
            SessionEntryType type,
            String id,
            Instant timestamp,
            Consumer<ObjectNode> payloadCustomizer
    ) throws IOException {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(payloadCustomizer, "payloadCustomizer");
        if (type == SessionEntryType.SESSION || type == SessionEntryType.UNKNOWN) {
            throw new IllegalArgumentException("cannot append entry type: " + type);
        }

        ObjectNode payload = codec.createObjectNode();
        payload.put("type", type.wireName());
        payload.put("id", id);
        if (activeEntryId == null) {
            payload.putNull("parentId");
        } else {
            payload.put("parentId", activeEntryId);
        }
        payload.put("timestamp", timestamp.toString());
        payloadCustomizer.accept(payload);

        String line = codec.writeJson(payload);
        appendFreshLineWithLock(line);
        SessionEntry entry = codec.parseLine(line, entries.size() + 2);
        entries.add(entry);
        activeEntryId = entry.id();
        return entry;
    }

    private void appendFreshLineWithLock(String line) throws IOException {
        appendFreshLinesWithLock(List.of(line));
    }

    private void appendFreshLinesWithLock(List<String> lines) throws IOException {
        Files.createDirectories(sessionFile.toAbsolutePath().getParent());
        try (FileChannel channel = FileChannel.open(
                sessionFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND);
             FileLock ignored = channel.lock()) {
            assertSessionSnapshotIsFresh();
            for (String line : lines) {
                byte[] bytes = (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
                channel.write(ByteBuffer.wrap(bytes));
            }
        }
    }

    private void validateAppendBatch(List<SessionEntry> appended) {
        SessionTree.from(new SessionDocument(header, java.util.stream.Stream
                .concat(entries.stream(), appended.stream())
                .toList()));
    }

    private void assertSessionSnapshotIsFresh() throws IOException {
        SessionDocument diskDocument;
        try (StringReader reader = new StringReader(Files.readString(sessionFile))) {
            diskDocument = codec.read(reader);
        }
        if (!header.payload().equals(diskDocument.header().payload())
                || entries.size() != diskDocument.entries().size()) {
            throw staleSessionSnapshot();
        }
        for (int i = 0; i < entries.size(); i++) {
            if (!entries.get(i).payload().equals(diskDocument.entries().get(i).payload())) {
                throw staleSessionSnapshot();
            }
        }
    }

    private IllegalStateException staleSessionSnapshot() {
        return new IllegalStateException("session file changed on disk; reopen before appending: " + sessionFile);
    }

    private ObjectNode toSessionMessage(AgentMessage agentMessage) {
        ObjectNode message = codec.createObjectNode();
        message.put("role", agentMessage.role().wireName());
        if (agentMessage.content() != null && !agentMessage.content().isNull()) {
            message.set("content", agentMessage.content());
        }
        if (agentMessage.metadata() != null && agentMessage.metadata().isObject()) {
            agentMessage.metadata().fields().forEachRemaining(field -> {
                if (!message.has(field.getKey())) {
                    message.set(field.getKey(), field.getValue());
                }
            });
            if (agentMessage.metadata().has("error") && !message.has("isError")) {
                message.set("isError", agentMessage.metadata().get("error"));
            }
        }
        return message;
    }

    private ObjectNode agentMessagePayload(AgentMessage agentMessage, String parentId) {
        ObjectNode payload = codec.createObjectNode();
        payload.put("type", SessionEntryType.MESSAGE.wireName());
        payload.put("id", agentMessage.id());
        if (parentId == null) {
            payload.putNull("parentId");
        } else {
            payload.put("parentId", parentId);
        }
        payload.put("timestamp", agentMessage.timestamp().toString());
        payload.set("message", toSessionMessage(agentMessage));
        return payload;
    }

    private static java.util.Optional<AgentMessage> toAgentMessage(SessionEntry entry) {
        return entry.message().map(message -> {
            ObjectNode metadata = JSON.objectNode();
            JsonNode payload = message.payload();
            if (payload != null && payload.isObject()) {
                payload.fields().forEachRemaining(field -> {
                    if (!field.getKey().equals("role") && !field.getKey().equals("content")) {
                        metadata.set(field.getKey(), field.getValue());
                    }
                });
                if (!metadata.has("error") && metadata.has("isError")) {
                    metadata.set("error", metadata.get("isError"));
                }
            }
            return new AgentMessage(
                    entry.id(),
                    entry.parentId(),
                    entry.timestamp(),
                    AgentMessageRole.fromWireName(payload != null ? payload.path("role").asText(null) : null),
                    message.content(),
                    metadata);
        });
    }

    private SessionEntry derivedHeader() throws IOException {
        ObjectNode payload = header.payload().deepCopy();
        payload.put("id", UUID.randomUUID().toString());
        payload.put("timestamp", Instant.now(clock).toString());
        if (header.id() != null) {
            payload.put("sourceSessionId", header.id());
        }
        if (activeEntryId != null) {
            payload.put("forkedFromEntryId", activeEntryId);
        }
        return codec.parseLine(codec.writeJson(payload), 1);
    }

    private static void writeDocument(Path path, SessionJsonlCodec codec, SessionDocument document) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            codec.write(document, writer);
        }
    }

    private static void appendLineWithLock(Path path, String line) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        byte[] bytes = (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND);
             FileLock ignored = channel.lock()) {
            channel.write(ByteBuffer.wrap(bytes));
        }
    }
}
