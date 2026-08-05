package com.agent4j.coding.sdk;

import com.agent4j.ai.AiModelClient;
import com.agent4j.coding.message.CodingAgentMessageConverter;
import com.agent4j.coding.runtime.CodingAgentLoopRequestFactory;
import com.agent4j.coding.runtime.CodingBranchSummarizer;
import com.agent4j.coding.runtime.CodingSessionCompactor;
import com.agent4j.core.event.AgentEventBus;
import com.agent4j.core.runtime.AgentMessageConverter;
import com.agent4j.core.tool.InMemoryToolRegistry;
import com.agent4j.core.tool.ToolRegistry;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

public record CodingAgentRuntimeServices(
        AgentEventBus eventBus,
        AiModelClient modelClient,
        ToolRegistry toolRegistry,
        AgentMessageConverter messageConverter,
        Clock clock,
        CodingAgentLoopRequestFactory requestFactory,
        CodingSessionCompactor sessionCompactor,
        CodingBranchSummarizer branchSummarizer
) {
    public CodingAgentRuntimeServices {
        Objects.requireNonNull(eventBus, "eventBus");
        Objects.requireNonNull(toolRegistry, "toolRegistry");
        Objects.requireNonNull(messageConverter, "messageConverter");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(requestFactory, "requestFactory");
        Objects.requireNonNull(sessionCompactor, "sessionCompactor");
        Objects.requireNonNull(branchSummarizer, "branchSummarizer");
    }

    public static CodingAgentRuntimeServices defaults() {
        return builder().build();
    }

    public static CodingAgentRuntimeServices withModelClient(AiModelClient modelClient) {
        return builder().modelClient(modelClient).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<AiModelClient> optionalModelClient() {
        return Optional.ofNullable(modelClient);
    }

    public static final class Builder {
        private AgentEventBus eventBus;
        private AiModelClient modelClient;
        private ToolRegistry toolRegistry;
        private AgentMessageConverter messageConverter;
        private Clock clock;
        private CodingAgentLoopRequestFactory requestFactory;
        private CodingSessionCompactor sessionCompactor;
        private CodingBranchSummarizer branchSummarizer;

        public Builder eventBus(AgentEventBus eventBus) {
            this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
            return this;
        }

        public Builder modelClient(AiModelClient modelClient) {
            this.modelClient = modelClient;
            return this;
        }

        public Builder toolRegistry(ToolRegistry toolRegistry) {
            this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
            return this;
        }

        public Builder messageConverter(AgentMessageConverter messageConverter) {
            this.messageConverter = Objects.requireNonNull(messageConverter, "messageConverter");
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        public Builder requestFactory(CodingAgentLoopRequestFactory requestFactory) {
            this.requestFactory = Objects.requireNonNull(requestFactory, "requestFactory");
            return this;
        }

        public Builder sessionCompactor(CodingSessionCompactor sessionCompactor) {
            this.sessionCompactor = Objects.requireNonNull(sessionCompactor, "sessionCompactor");
            return this;
        }

        public Builder branchSummarizer(CodingBranchSummarizer branchSummarizer) {
            this.branchSummarizer = Objects.requireNonNull(branchSummarizer, "branchSummarizer");
            return this;
        }

        public CodingAgentRuntimeServices build() {
            AgentEventBus resolvedEventBus = eventBus == null ? new AgentEventBus() : eventBus;
            Clock resolvedClock = clock == null ? Clock.systemUTC() : clock;
            return new CodingAgentRuntimeServices(
                    resolvedEventBus,
                    modelClient,
                    toolRegistry == null ? InMemoryToolRegistry.builder().build() : toolRegistry,
                    messageConverter == null ? CodingAgentMessageConverter.INSTANCE : messageConverter,
                    resolvedClock,
                    requestFactory == null ? new CodingAgentLoopRequestFactory() : requestFactory,
                    sessionCompactor == null ? new CodingSessionCompactor(resolvedEventBus) : sessionCompactor,
                    branchSummarizer == null ? new CodingBranchSummarizer() : branchSummarizer);
        }
    }
}
