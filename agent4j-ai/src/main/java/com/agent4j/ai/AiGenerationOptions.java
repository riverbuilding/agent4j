package com.agent4j.ai;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record AiGenerationOptions(
        Optional<Integer> maxOutputTokens,
        Optional<Double> temperature,
        Optional<Double> topP,
        Optional<Integer> topK,
        Optional<String> toolChoice,
        boolean parallelToolCalls,
        Map<String, String> metadata
) {
    public AiGenerationOptions {
        Objects.requireNonNull(maxOutputTokens, "maxOutputTokens");
        Objects.requireNonNull(temperature, "temperature");
        Objects.requireNonNull(topP, "topP");
        Objects.requireNonNull(topK, "topK");
        Objects.requireNonNull(toolChoice, "toolChoice");
        Objects.requireNonNull(metadata, "metadata");
        maxOutputTokens.ifPresent(value -> {
            if (value <= 0) {
                throw new IllegalArgumentException("maxOutputTokens must be positive");
            }
        });
        temperature.ifPresent(AiGenerationOptions::validateTemperature);
        topP.ifPresent(AiGenerationOptions::validateTopP);
        topK.ifPresent(value -> {
            if (value <= 0) {
                throw new IllegalArgumentException("topK must be positive");
            }
        });
        toolChoice = toolChoice
                .map(String::strip)
                .filter(value -> !value.isBlank());
        metadata = Map.copyOf(metadata);
    }

    public static AiGenerationOptions defaults() {
        return new AiGenerationOptions(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                true,
                Map.of());
    }

    private static void validateTemperature(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0 || value > 2.0) {
            throw new IllegalArgumentException("temperature must be between 0 and 2");
        }
    }

    private static void validateTopP(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("topP must be between 0 and 1");
        }
    }
}
