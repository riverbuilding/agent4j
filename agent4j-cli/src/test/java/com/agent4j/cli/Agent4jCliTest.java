package com.agent4j.cli;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiTextContent;
import com.agent4j.ai.AiUsage;
import com.agent4j.ai.AiAuthMode;
import com.agent4j.ai.AiResolvedAuth;
import com.agent4j.coding.resource.ResourceDiscovery;
import com.agent4j.coding.resource.ResourceDiscoveryOptions;
import com.agent4j.coding.resource.ResourceLoader;
import com.agent4j.coding.sdk.CodingAgentRuntimeServices;
import com.agent4j.coding.sdk.AgentSessionRuntime;
import com.agent4j.coding.sdk.CodingAgentSessionRuntime;
import com.agent4j.coding.sdk.AuthSession;
import com.agent4j.coding.sdk.AuthStatus;
import com.agent4j.coding.sdk.LoginService;
import com.agent4j.coding.sdk.ApiKeyLoginRequest;
import com.agent4j.coding.sdk.AccessTokenLoginRequest;
import com.agent4j.coding.sdk.BrowserSubscriptionLoginRequest;
import com.agent4j.coding.sdk.DeviceCodeSubscriptionLoginRequest;
import com.agent4j.coding.sdk.SubscriptionLoginCompletion;
import com.agent4j.coding.sdk.SubscriptionLoginPollResult;
import com.agent4j.coding.sdk.SubscriptionLoginStart;
import com.agent4j.core.tool.InMemoryToolRegistry;
import com.agent4j.testkit.ai.FakeModelClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class Agent4jCliTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void helpDoesNotCreateARuntime() {
        CliRuntimeFactory factory = request -> {
            throw new AssertionError("help must not create a runtime");
        };

        int exitCode = execute(factory, "--help");

        assertThat(exitCode).isZero();
    }

    @Test
    void rootCommandPassesParsedRuntimeInputsToInjectedFactory() throws Exception {
        AtomicReference<CliRuntimeRequest> received = new AtomicReference<>();
        CliRuntimeFactory factory = request -> {
            received.set(request);
            return runtime();
        };

        int exitCode = execute(
                factory,
                "--mode", "json",
                "--mode", "rpc",
                "--print",
                "--provider", "openai",
                "--model", "gpt-test",
                "--api-key", "sk-runtime-only",
                "hello");

        assertThat(exitCode).isEqualTo(1);
        assertThat(received.get()).isNotNull();
        assertThat(received.get().cwd()).isEqualTo(temporaryDirectory.resolve("workspace").toAbsolutePath());
        assertThat(received.get().provider()).contains("openai");
        assertThat(received.get().model()).contains("gpt-test");
        assertThat(received.get().apiKey()).contains("sk-runtime-only");
    }

    @Test
    void printOptionRunsPromptAndWritesAssistantTextToConfiguredStdout() throws Exception {
        FakeModelClient model = new FakeModelClient().enqueue(List.of(new AiStreamEvent.MessageCompleted(
                "assistant-1",
                new AiAssistantMessage(
                        List.of(new AiTextContent("command answer")),
                        AiStopReason.STOP,
                        AiUsage.zero()))));
        CliRuntimeFactory factory = request -> runtime(model);
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();

        int exitCode = Agent4jCli.execute(
                factory,
                environment(),
                new PrintWriter(stdout),
                new PrintWriter(stderr),
                "-p", "say hello");

        assertThat(exitCode).isZero();
        assertThat(stdout.toString()).isEqualTo("command answer\n");
        assertThat(stderr.toString()).isEmpty();
    }

    @Test
    void jsonModeTakesPrecedenceOverPrintAndWritesOnlyJsonLines() throws Exception {
        FakeModelClient model = new FakeModelClient().enqueue(List.of(new AiStreamEvent.MessageCompleted(
                "assistant-1",
                new AiAssistantMessage(
                        List.of(new AiTextContent("command answer")),
                        AiStopReason.STOP,
                        AiUsage.zero()))));
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();

        int exitCode = Agent4jCli.execute(
                request -> runtime(model),
                environment(),
                new PrintWriter(stdout),
                new PrintWriter(stderr),
                "-p", "--mode", "json", "say hello");

        assertThat(exitCode).isZero();
        assertThat(stdout.toString().lines().allMatch(line -> line.startsWith("{"))).isTrue();
        assertThat(stdout.toString()).contains("\"type\":\"session\"", "\"type\":\"agent_end\"");
        assertThat(stdout.toString()).doesNotContain("command answer\n");
        assertThat(stderr.toString()).isEmpty();
    }

    @Test
    void commandLineToolFlagsArePassedToTheRuntimeRequest() throws Exception {
        AtomicReference<CliRuntimeRequest> received = new AtomicReference<>();

        int exitCode = execute(request -> {
            received.set(request);
            return runtime();
        }, "-p", "--tools", "read,grep", "--exclude-tools", "grep", "hello");

        assertThat(exitCode).isEqualTo(1);
        assertThat(received.get().toolSelection().included()).contains(List.of("read", "grep"));
        assertThat(received.get().toolSelection().excluded()).containsExactly("grep");
    }

    @Test
    void invalidSessionFlagsFailBeforeRuntimeBootstrap() {
        StringWriter stderr = new StringWriter();
        CliRuntimeFactory factory = request -> {
            throw new AssertionError("invalid session flags must not construct a runtime");
        };

        int exitCode = Agent4jCli.execute(
                factory,
                environment(),
                new PrintWriter(new StringWriter()),
                new PrintWriter(stderr),
                "--print", "--fork", "source", "--continue", "hello");

        assertThat(exitCode).isEqualTo(1);
        assertThat(stderr.toString()).contains("--fork cannot be combined");
    }

    @Test
    void authCommandsDelegateToLoginServiceWithoutPrintingSecrets() throws Exception {
        FakeLoginService login = new FakeLoginService();
        CliRuntimeFactory factory = request -> runtime(null, login);
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();

        int statusExit = Agent4jCli.execute(factory, environment(), new PrintWriter(stdout), new PrintWriter(stderr), "auth-status");
        int refreshExit = Agent4jCli.execute(factory, environment(), new PrintWriter(stdout), new PrintWriter(stderr), "refresh");
        int logoutExit = Agent4jCli.execute(factory, environment(), new PrintWriter(stdout), new PrintWriter(stderr), "logout");

        assertThat(statusExit).isZero();
        assertThat(refreshExit).isZero();
        assertThat(logoutExit).isZero();
        assertThat(login.refreshCalls).isEqualTo(1);
        assertThat(login.logoutCalls).isEqualTo(1);
        assertThat(stdout.toString()).contains("authenticated: true", "Logged out: openai");
        assertThat(stdout.toString()).doesNotContain("access-token", "refresh-token", "secret");
        assertThat(stderr.toString()).isEmpty();
    }

    @Test
    void loginUsesTheOpenAiBrowserFlowAndAuthBootstrapDoesNotRequireAModelFlag() throws Exception {
        FakeLoginService login = new FakeLoginService();
        AtomicReference<CliRuntimeRequest> request = new AtomicReference<>();
        StringWriter stdout = new StringWriter();

        int exitCode = Agent4jCli.execute(input -> {
                    request.set(input);
                    return runtime(null, login);
                },
                environment(), new PrintWriter(stdout), new PrintWriter(new StringWriter()), "login");

        assertThat(exitCode).isZero();
        assertThat(login.browserLoginCalls).isEqualTo(1);
        assertThat(request.get().provider()).contains("openai");
        assertThat(request.get().model()).contains("gpt-5");
        assertThat(stdout.toString()).doesNotContain("access-token", "refresh-token");
    }

    private CliEnvironment environment() {
        return new CliEnvironment(temporaryDirectory.resolve("workspace"), temporaryDirectory.resolve("home"));
    }

    private int execute(CliRuntimeFactory factory, String... args) {
        return Agent4jCli.execute(
                factory,
                environment(),
                new PrintWriter(new StringWriter()),
                new PrintWriter(new StringWriter()),
                args);
    }

    private CliRuntime runtime() throws Exception {
        return runtime(null);
    }

    private CliRuntime runtime(FakeModelClient model) throws Exception {
        return runtime(model, new FakeLoginService());
    }

    private CliRuntime runtime(FakeModelClient model, LoginService loginService) throws Exception {
        CliEnvironment environment = environment();
        Files.createDirectories(environment.cwd());
        ResourceDiscovery discovery = new ResourceLoader().discover(
                ResourceDiscoveryOptions.enabled(environment.homeDirectory(), environment.cwd()));
        CodingAgentRuntimeServices.Builder services = CodingAgentRuntimeServices.builder()
                .toolRegistry(InMemoryToolRegistry.builder().build())
                .clock(Clock.systemUTC())
                .loginService(loginService);
        if (model != null) {
            services.modelClient(model);
        }
        AgentSessionRuntime runtime = new CodingAgentSessionRuntime(services.build());
        return new CliRuntime(runtime, discovery, new AiModelReference("openai", "gpt-test"));
    }

    private static final class FakeLoginService implements LoginService {
        private int refreshCalls;
        private int logoutCalls;
        private int browserLoginCalls;

        @Override public AuthSession loginApiKey(ApiKeyLoginRequest request) { throw new UnsupportedOperationException(); }
        @Override public AuthSession loginAccessToken(AccessTokenLoginRequest request) { throw new UnsupportedOperationException(); }
        @Override public SubscriptionLoginStart startBrowserSubscriptionLogin(BrowserSubscriptionLoginRequest request) { throw new UnsupportedOperationException(); }
        @Override public AuthStatus loginOpenAiSubscription() { browserLoginCalls++; return status("openai"); }
        @Override public SubscriptionLoginStart startDeviceCodeSubscriptionLogin(DeviceCodeSubscriptionLoginRequest request) { throw new UnsupportedOperationException(); }
        @Override public AuthSession completeSubscriptionLogin(SubscriptionLoginCompletion completion) { throw new UnsupportedOperationException(); }
        @Override public SubscriptionLoginPollResult pollSubscriptionLogin(String flowId) { throw new UnsupportedOperationException(); }
        @Override public SubscriptionLoginPollResult completeBrowserSubscriptionLoginCallback(String code, String state) { throw new UnsupportedOperationException(); }
        @Override public SubscriptionLoginPollResult completeBrowserSubscriptionLoginErrorCallback(String error, Optional<String> state) { throw new UnsupportedOperationException(); }
        @Override public boolean cancelSubscriptionLogin(String flowId) { return false; }
        @Override public Optional<AuthSession> refreshAuth(String providerId) { refreshCalls++; return Optional.empty(); }
        @Override public AuthStatus status(String providerId) {
            return new AuthStatus(providerId, AiAuthMode.CHATGPT_SUBSCRIPTION, true, false,
                    Optional.of(Instant.parse("2026-08-12T00:00:00Z")), Optional.of("test"),
                    Map.of("refreshToken", "refresh-token", "accessToken", "access-token", "note", "secret"));
        }
        @Override public AiResolvedAuth resolveAuth(String providerId) { return AiResolvedAuth.none(); }
        @Override public boolean logout(String providerId) { logoutCalls++; return true; }
    }
}
