package com.agent4j.coding.sdk;

import com.agent4j.ai.AiModelReference;
import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.AgentEventBus;
import com.agent4j.core.event.EventSubscription;
import com.agent4j.core.runtime.AgentConversationContext;
import com.agent4j.core.runtime.QueueMode;
import com.agent4j.core.runtime.ToolExecutionMode;
import com.agent4j.testkit.ai.FakeModelClient;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentSessionRuntimeInterfaceTest {
    @Test
    void createSessionRequestNormalizesPathsAndDefaultsOptionalFields() {
        CreateSessionRequest request = new CreateSessionRequest(Path.of("sessions/a.jsonl"), Path.of("."));

        assertThat(request.sessionFile()).isAbsolute();
        assertThat(request.cwd()).isAbsolute();
        assertThat(request.name()).isEmpty();
        assertThat(request.model()).isEmpty();
    }

    @Test
    void createSessionRequestRejectsBlankName() {
        assertThatThrownBy(() -> new CreateSessionRequest(
                        Path.of("session.jsonl"),
                        Path.of("."),
                        Optional.of(" "),
                        Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void resumeSessionRequestCarriesOptionalModelAndActiveEntry() {
        AiModelReference model = new AiModelReference("openai", "gpt-4.1");
        ResumeSessionRequest request = new ResumeSessionRequest(
                Path.of("session.jsonl"),
                Optional.of("entry-1"),
                Optional.of(model));

        assertThat(request.sessionFile()).isAbsolute();
        assertThat(request.activeEntryId()).contains("entry-1");
        assertThat(request.model()).contains(model);
    }

    @Test
    void promptRequestDefaultsToParallelQueueOneAtATimeAndNoAbortSignal() {
        PromptRequest request = new PromptRequest("hello");

        assertThat(request.model()).isEmpty();
        assertThat(request.maxToolRounds()).isZero();
        assertThat(request.maxModelRetries()).isZero();
        assertThat(request.modelTimeout()).isEmpty();
        assertThat(request.toolExecutionMode()).isEqualTo(ToolExecutionMode.PARALLEL);
        assertThat(request.toolAttributes()).isEmpty();
        assertThat(request.steeringMessages()).isEmpty();
        assertThat(request.followUpMessages()).isEmpty();
        assertThat(request.steeringMode()).isEqualTo(QueueMode.ONE_AT_A_TIME);
        assertThat(request.followUpMode()).isEqualTo(QueueMode.ONE_AT_A_TIME);
        assertThat(request.abortSignal()).isEmpty();
    }

    @Test
    void promptRequestDefensivelyCopiesCollections() {
        List<com.agent4j.core.message.AgentMessage> steering = new ArrayList<>();
        PromptRequest request = new PromptRequest(
                "hello",
                null,
                1,
                2,
                Optional.of(Duration.ofSeconds(5)),
                null,
                java.util.Map.of("key", "value"),
                steering,
                null,
                null,
                null,
                null);

        steering.clear();

        assertThat(request.model()).isEmpty();
        assertThat(request.maxToolRounds()).isEqualTo(1);
        assertThat(request.maxModelRetries()).isEqualTo(2);
        assertThat(request.modelTimeout()).contains(Duration.ofSeconds(5));
        assertThat(request.toolExecutionMode()).isEqualTo(ToolExecutionMode.PARALLEL);
        assertThat(request.toolAttributes()).containsEntry("key", "value");
        assertThat(request.steeringMessages()).isEmpty();
        assertThat(request.followUpMessages()).isEmpty();
    }

    @Test
    void promptRequestRejectsInvalidValues() {
        assertThatThrownBy(() -> new PromptRequest(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt");

        assertThatThrownBy(() -> new PromptRequest(
                        "hello",
                        Optional.empty(),
                        -1,
                        0,
                        Optional.empty(),
                        ToolExecutionMode.PARALLEL,
                        java.util.Map.of(),
                        List.of(),
                        List.of(),
                        QueueMode.ONE_AT_A_TIME,
                        QueueMode.ONE_AT_A_TIME,
                        Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxToolRounds");

        assertThatThrownBy(() -> new PromptRequest(
                        "hello",
                        Optional.empty(),
                        0,
                        0,
                        Optional.of(Duration.ZERO),
                        ToolExecutionMode.PARALLEL,
                        java.util.Map.of(),
                        List.of(),
                        List.of(),
                        QueueMode.ONE_AT_A_TIME,
                        QueueMode.ONE_AT_A_TIME,
                        Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelTimeout");
    }

    @Test
    void subscribeSessionFiltersEventsBySessionId() {
        TestRuntime runtime = new TestRuntime();
        List<AgentEvent> received = new ArrayList<>();
        EventSubscription subscription = runtime.subscribeSession("session-1", received::add);

        runtime.publish(new AgentEvent.AgentStarted("session-2", Instant.EPOCH, "turn-1"));
        runtime.publish(new AgentEvent.AgentStarted("session-1", Instant.EPOCH, "turn-2"));
        subscription.close();
        runtime.publish(new AgentEvent.AgentStarted("session-1", Instant.EPOCH, "turn-3"));

        assertThat(received)
                .singleElement()
                .extracting(event -> ((AgentEvent.AgentStarted) event).turnId())
                .isEqualTo("turn-2");
    }

    @Test
    void runtimeServicesDefaultsProvideSharedCoreServices() {
        CodingAgentRuntimeServices services = CodingAgentRuntimeServices.defaults();

        assertThat(services.eventBus()).isNotNull();
        assertThat(services.optionalModelClient()).isEmpty();
        assertThat(services.toolRegistry()).isNotNull();
        assertThat(services.messageConverter()).isNotNull();
        assertThat(services.clock()).isNotNull();
        assertThat(services.requestFactory()).isNotNull();
        assertThat(services.sessionCompactor()).isNotNull();
        assertThat(services.branchSummarizer()).isNotNull();
    }

    @Test
    void runtimeServicesBuilderCarriesConfiguredServices() {
        AgentEventBus eventBus = new AgentEventBus();
        FakeModelClient modelClient = new FakeModelClient();
        Clock clock = Clock.fixed(Instant.parse("2026-08-05T12:00:00Z"), ZoneOffset.UTC);

        CodingAgentRuntimeServices services = CodingAgentRuntimeServices.builder()
                .eventBus(eventBus)
                .modelClient(modelClient)
                .clock(clock)
                .build();

        assertThat(services.eventBus()).isSameAs(eventBus);
        assertThat(services.optionalModelClient()).containsSame(modelClient);
        assertThat(services.clock()).isSameAs(clock);
    }

    private static final class TestRuntime implements AgentSessionRuntime {
        private final AgentEventBus eventBus = new AgentEventBus();

        @Override
        public AgentSession createSession(CreateSessionRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentSession resumeSession(ResumeSessionRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentSession importSession(ImportSessionRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentSession cloneSession(CloneSessionRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentSession forkSession(ForkSessionRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LoginService loginService() {
            return CodingAgentRuntimeServices.defaults().loginService();
        }

        @Override
        public EventSubscription subscribe(Consumer<AgentEvent> subscriber) {
            return eventBus.subscribe(subscriber);
        }

        void publish(AgentEvent event) {
            eventBus.publish(event);
        }
    }

    private static final class TestSession implements AgentSession {
        @Override
        public AgentSessionInfo info() {
            return new AgentSessionInfo("session-1", Path.of("session.jsonl"), Path.of("."), null);
        }

        @Override
        public AgentConversationContext conversationContext() {
            return new AgentConversationContext(List.of(), List.of());
        }

        @Override
        public PromptResult prompt(PromptRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
