package com.agent4j.coding.extension;

import com.agent4j.ai.AiProviderRequest;
import com.agent4j.core.message.AgentMessage;

import java.util.List;
import java.util.Objects;

/**
 * Java-only customization contract for a coding-agent runtime.
 *
 * <p>Extensions are supplied explicitly by application code in this release. They are not
 * discovered from project files or loaded dynamically.</p>
 */
public interface CodingAgentExtension {
    /** A stable, unique extension identifier. */
    String name();

    /** Registers the extension's tools, commands, and lifecycle listeners during runtime setup. */
    default void register(CodingExtensionRegistrar registrar) throws Exception {
        Objects.requireNonNull(registrar, "registrar");
    }

    /** Runs after session construction and before the session is exposed to callers. */
    default void onSessionStart(CodingExtensionContext context) throws Exception {
    }

    /** Runs before a user prompt starts an agent loop. The returned event is passed to the next extension. */
    default CodingExtensionAgentStart beforeAgentStart(
            CodingExtensionAgentStart event,
            CodingExtensionContext context
    ) throws Exception {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(context, "context");
        return event;
    }

    /** Runs before each model request. The returned messages are passed to the next extension. */
    default List<AgentMessage> transformContext(
            List<AgentMessage> messages,
            CodingExtensionContext context
    ) throws Exception {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(context, "context");
        return messages;
    }

    /** Runs before a provider request is sent. The returned request is passed to the next extension. */
    default AiProviderRequest beforeProviderRequest(
            AiProviderRequest request,
            CodingExtensionContext context
    ) throws Exception {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(context, "context");
        return request;
    }

    /** Runs after the provider response headers are received and before its stream is consumed. */
    default void afterProviderResponse(
            CodingExtensionProviderResponse response,
            CodingExtensionContext context
    ) throws Exception {
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(context, "context");
    }

    /** Runs before the session is closed or replaced. */
    default void onSessionShutdown(CodingExtensionContext context) throws Exception {
    }
}
