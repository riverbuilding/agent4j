package com.agent4j.coding.sdk.example;

import com.agent4j.ai.AiModelReference;
import com.agent4j.coding.sdk.AgentSession;
import com.agent4j.coding.sdk.AuthSession;
import com.agent4j.coding.sdk.AuthStatus;
import com.agent4j.coding.sdk.CodingAgentRuntimeServices;
import com.agent4j.coding.sdk.CodingAgentSessionRuntime;
import com.agent4j.coding.sdk.CreateSessionRequest;
import com.agent4j.coding.sdk.DeviceCodeSubscriptionLoginRequest;
import com.agent4j.coding.sdk.LoginService;
import com.agent4j.coding.sdk.OpenAiCodingRuntimeOptions;
import com.agent4j.coding.sdk.PersistentAuthCredentialStore;
import com.agent4j.coding.sdk.PromptRequest;
import com.agent4j.coding.sdk.ResumeSessionRequest;
import com.agent4j.coding.sdk.SubscriptionLoginPollResult;
import com.agent4j.coding.sdk.SubscriptionLoginStart;
import com.agent4j.coding.sdk.SubscriptionLoginStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Runnable SDK example. Set AGENT4J_OPENAI_MODEL before invoking a command.
 */
public final class OpenAiSubscriptionSdkExample {
    private static final String OPENAI_PROVIDER_ID = "openai";

    private OpenAiSubscriptionSdkExample() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("help")) {
            usage();
            return;
        }

        AiModelReference model = new AiModelReference(OPENAI_PROVIDER_ID, requiredModel());
        CodingAgentRuntimeServices services = CodingAgentRuntimeServices.withOpenAi(
                OpenAiCodingRuntimeOptions.builder(model)
                        .credentialStore(PersistentAuthCredentialStore.userDefault())
                        .build());
        LoginService loginService = services.loginService();

        switch (args[0]) {
            case "browser" -> printStatus(loginService.loginOpenAiSubscription());
            case "device" -> deviceLogin(loginService);
            case "status" -> printStatus(loginService.status(OPENAI_PROVIDER_ID));
            case "refresh" -> printRefresh(loginService.refreshAuth(OPENAI_PROVIDER_ID));
            case "logout" -> System.out.println(loginService.logout(OPENAI_PROVIDER_ID)
                    ? "OpenAI credentials removed."
                    : "No OpenAI credentials were stored.");
            case "prompt" -> prompt(services, args);
            default -> usage();
        }
    }

    private static void deviceLogin(LoginService loginService) throws InterruptedException {
        SubscriptionLoginStart start = loginService.startDeviceCodeSubscriptionLogin(
                new DeviceCodeSubscriptionLoginRequest(OPENAI_PROVIDER_ID));
        System.out.println("Open: " + start.authorizationUri());
        start.userCode().ifPresent(code -> System.out.println("Code: " + code));

        while (true) {
            SubscriptionLoginPollResult result = loginService.pollSubscriptionLogin(start.flowId());
            if (result.status() == SubscriptionLoginStatus.COMPLETED) {
                printStatus(loginService.status(OPENAI_PROVIDER_ID));
                return;
            }
            if (result.status() == SubscriptionLoginStatus.FAILED || result.status() == SubscriptionLoginStatus.EXPIRED) {
                throw new IllegalStateException(result.error().orElse("OpenAI device login failed"));
            }
            long waitMillis = result.retryAfter()
                    .map(retryAfter -> Math.max(1, Duration.between(Instant.now(), retryAfter).toMillis()))
                    .orElse(1_000L);
            Thread.sleep(waitMillis);
        }
    }

    private static void prompt(CodingAgentRuntimeServices services, String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException("prompt requires <session-file> <prompt text>");
        }
        Path sessionFile = Path.of(args[1]).toAbsolutePath().normalize();
        String prompt = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        CodingAgentSessionRuntime runtime = new CodingAgentSessionRuntime(services);
        AgentSession session = Files.exists(sessionFile)
                ? runtime.resumeSession(new ResumeSessionRequest(sessionFile))
                : runtime.createSession(new CreateSessionRequest(sessionFile, Path.of(".")));
        var result = session.prompt(new PromptRequest(prompt));
        System.out.println("Session: " + result.session().id());
        System.out.println("Assistant messages: " + result.loopResult().assistantMessages().size());
    }

    private static void printRefresh(Optional<AuthSession> session) {
        if (session.isEmpty()) {
            System.out.println("OpenAI credentials could not be refreshed.");
            return;
        }
        System.out.println("OpenAI credentials refreshed.");
    }

    private static void printStatus(AuthStatus status) {
        System.out.println("Provider: " + status.providerId());
        System.out.println("Authenticated: " + status.authenticated());
        System.out.println("Mode: " + status.mode().wireName());
        System.out.println("Expired: " + status.expired());
        status.expiresAt().ifPresent(value -> System.out.println("Expires at: " + value));
        Optional.ofNullable(status.metadata().get("plan")).ifPresent(value -> System.out.println("Plan: " + value));
    }

    private static String requiredModel() {
        String model = System.getenv("AGENT4J_OPENAI_MODEL");
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("AGENT4J_OPENAI_MODEL must name an enabled OpenAI model");
        }
        return model;
    }

    private static void usage() {
        System.out.println("Usage: OpenAiSubscriptionSdkExample <browser|device|status|refresh|logout|prompt>");
        System.out.println("Set AGENT4J_OPENAI_MODEL before running a command.");
        System.out.println("prompt requires: prompt <session-file> <prompt text>");
    }
}
