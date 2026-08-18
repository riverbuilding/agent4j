package com.agent4j.coding.extension;

import com.agent4j.ai.AiProvider;
import com.agent4j.ai.AiProviderRequest;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiStreamOptions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Applies safe request mutations and observes provider streams without exposing credentials. */
public final class ExtensionProviderHookDispatcher {
    private static final System.Logger LOGGER = System.getLogger(ExtensionProviderHookDispatcher.class.getName());
    private ExtensionProviderHookDispatcher() { }

    public static AiProvider wrap(AiProvider delegate, List<ExtensionProviderHookContribution> contributions) {
        if (contributions.isEmpty()) return delegate;
        return new AiProvider() {
            @Override public String id() { return delegate.id(); }
            @Override public String name() { return delegate.name(); }
            @Override public com.agent4j.ai.AiProviderApi api() { return delegate.api(); }
            @Override public com.agent4j.ai.AiProviderFeatures features() { return delegate.features(); }
            @Override public List<com.agent4j.ai.AiModel> models() { return delegate.models(); }
            @Override public void stream(AiProviderRequest request, Consumer<AiStreamEvent> sink) throws Exception {
                AiProviderRequest current = request;
                for (ExtensionProviderHookContribution contribution : contributions) {
                    try { current = apply(current, contribution.hook().beforeRequest(view(current))); }
                    catch (Exception error) { report(contribution, "request", error); }
                }
                try {
                    delegate.stream(current, event -> {
                        for (ExtensionProviderHookContribution contribution : contributions) {
                            try {
                                contribution.hook().onStreamEvent(event);
                                if (event instanceof AiStreamEvent.MessageCompleted completed) contribution.hook().onCompletion(completed);
                            } catch (Exception error) { report(contribution, "response", error); }
                        }
                        sink.accept(event);
                    });
                } catch (Exception error) {
                    for (ExtensionProviderHookContribution contribution : contributions) {
                        try { contribution.hook().onFailure(error); } catch (Exception hookError) { report(contribution, "failure", hookError); }
                    }
                    throw error;
                }
            }
        };
    }
    private static ExtensionProviderRequest view(AiProviderRequest request) {
        return new ExtensionProviderRequest(request.model().reference(), request.context().auth().mode(), redact(request.options().headers()),
                request.options().attributes(), request.turn().messages().size(), request.turn().tools().size());
    }
    private static AiProviderRequest apply(AiProviderRequest request, ExtensionProviderRequestMutation mutation) {
        Map<String, String> headers = new LinkedHashMap<>(request.options().headers());
        mutation.headers().forEach((name, value) -> { if (sensitive(name)) throw new IllegalArgumentException("extension provider hooks may not set authentication header: " + name); headers.put(name, value); });
        Map<String, Object> attributes = new LinkedHashMap<>(request.options().attributes());
        attributes.putAll(mutation.attributes());
        AiStreamOptions options = request.options();
        return new AiProviderRequest(request.model(), request.turn(), request.context(), new AiStreamOptions(
                options.signal(), options.timeout(), options.maxRetries(), headers, attributes, options.generation()));
    }
    private static Map<String, String> redact(Map<String, String> headers) {
        Map<String, String> safe = new LinkedHashMap<>(); headers.forEach((name, value) -> safe.put(name, sensitive(name) ? "[REDACTED]" : value)); return safe;
    }
    private static boolean sensitive(String name) { String lower = name.toLowerCase(Locale.ROOT); return lower.equals("authorization") || lower.equals("api-key") || lower.equals("x-api-key"); }
    private static void report(ExtensionProviderHookContribution contribution, String phase, Exception error) {
        LOGGER.log(System.Logger.Level.WARNING, "extension {0} provider hook {1} failed during {2}: {3}", contribution.extensionName(), contribution.name(), phase, error.toString());
    }
}
