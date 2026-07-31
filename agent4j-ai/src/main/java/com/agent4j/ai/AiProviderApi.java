package com.agent4j.ai;

import java.util.Objects;

public enum AiProviderApi {
    ANTHROPIC_MESSAGES("anthropic-messages"),
    OPENAI_COMPLETIONS("openai-completions"),
    OPENAI_RESPONSES("openai-responses"),
    AZURE_OPENAI_RESPONSES("azure-openai-responses"),
    OPENAI_CODEX_RESPONSES("openai-codex-responses"),
    MISTRAL_CONVERSATIONS("mistral-conversations"),
    GOOGLE_GENERATIVE_AI("google-generative-ai"),
    GOOGLE_VERTEX("google-vertex"),
    BEDROCK_CONVERSE_STREAM("bedrock-converse-stream"),
    CUSTOM("custom");

    private final String wireName;

    AiProviderApi(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static AiProviderApi fromWireName(String wireName) {
        Objects.requireNonNull(wireName, "wireName");
        for (AiProviderApi value : values()) {
            if (value.wireName.equals(wireName)) {
                return value;
            }
        }
        return CUSTOM;
    }
}
