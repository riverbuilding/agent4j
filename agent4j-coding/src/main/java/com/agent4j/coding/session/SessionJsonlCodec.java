package com.agent4j.coding.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SessionJsonlCodec {
    private final ObjectMapper mapper;

    public SessionJsonlCodec() {
        this(defaultObjectMapper());
    }

    public SessionJsonlCodec(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public SessionDocument read(Reader reader) throws IOException {
        Objects.requireNonNull(reader, "reader");
        List<SessionEntry> parsed = new ArrayList<>();
        try (BufferedReader bufferedReader = new BufferedReader(reader)) {
            String line;
            int lineNumber = 0;
            while ((line = bufferedReader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                parsed.add(parseLine(line, lineNumber));
            }
        }
        if (parsed.isEmpty()) {
            throw new IOException("session JSONL is empty");
        }
        return new SessionDocument(parsed.getFirst(), parsed.subList(1, parsed.size()));
    }

    public void write(SessionDocument document, Writer writer) throws IOException {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(writer, "writer");
        try (BufferedWriter bufferedWriter = new BufferedWriter(writer)) {
            for (SessionEntry entry : document.allEntries()) {
                bufferedWriter.write(writeLine(entry));
                bufferedWriter.newLine();
            }
        }
    }

    public SessionEntry parseLine(String line, int lineNumber) throws IOException {
        try {
            JsonNode node = mapper.readTree(line);
            if (!(node instanceof ObjectNode objectNode)) {
                throw new IOException("line " + lineNumber + " is not a JSON object");
            }
            JsonNode typeNode = objectNode.get("type");
            if (typeNode == null || !typeNode.isTextual()) {
                throw new IOException("line " + lineNumber + " is missing textual type");
            }
            String id = textOrNull(objectNode.get("id"));
            String parentId = textOrNull(objectNode.get("parentId"));
            Instant timestamp = instantOrNull(objectNode.get("timestamp"), lineNumber);
            return new SessionEntry(typeNode.asText(), id, parentId, timestamp, objectNode.deepCopy());
        } catch (JsonProcessingException e) {
            throw new IOException("line " + lineNumber + " is not valid JSON", e);
        }
    }

    public String writeLine(SessionEntry entry) {
        return writeJson(entry.payload());
    }

    public String writeJson(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public ObjectNode createObjectNode() {
        return mapper.createObjectNode();
    }

    public com.fasterxml.jackson.databind.node.ArrayNode createArrayNode() {
        return mapper.createArrayNode();
    }

    public com.fasterxml.jackson.databind.node.TextNode textNode(String value) {
        return com.fasterxml.jackson.databind.node.TextNode.valueOf(value);
    }

    private static ObjectMapper defaultObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private static String textOrNull(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private static Instant instantOrNull(JsonNode node, int lineNumber) throws IOException {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new IOException("line " + lineNumber + " timestamp must be an ISO string");
        }
        return Instant.parse(node.asText());
    }
}
