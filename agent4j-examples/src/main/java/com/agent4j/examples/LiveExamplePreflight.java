package com.agent4j.examples;

/** Verifies local setup for future billable walkthroughs without sending an API request. */
public final class LiveExamplePreflight {
    private LiveExamplePreflight() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "help".equals(args[0])) {
            usage();
            return;
        }
        LiveExampleConfiguration configuration = LiveExampleConfiguration.open();
        var runtime = com.agent4j.coding.sdk.CodingAgentRuntime.create(configuration.toCodingAgentConfig());
        try (runtime) {
            System.out.println("Live example runtime is ready.");
            System.out.println("Model: " + configuration.model());
            System.out.println("Base URL: " + configuration.baseUrl().orElse("provider default"));
            System.out.println("Workspace: " + configuration.workspace());
            System.out.println("Session directory: " + configuration.sessionDirectory());
            System.out.println("Max output tokens: " + configuration.maxOutputTokens());
            System.out.println("Max tool rounds: " + configuration.maxToolRounds());
            System.out.println("Temporary workspace: " + configuration.temporaryWorkspace());
            System.out.println("Temporary session directory: " + configuration.temporarySessionDirectory());
            System.out.println("No API request was sent by this preflight check.");
        } finally {
            runtime.cleanupOwnedFiles();
        }
    }

    private static void usage() {
        System.out.println("Usage: LiveExamplePreflight [help]");
        System.out.println("Set AGENT4J_API_KEY and AGENT4J_MODEL before running the preflight check.");
        System.out.println("Set AGENT4J_MODEL to a catalog model (for example gpt-5 or claude-sonnet-4-5).");
        System.out.println("Optionally set AGENT4J_BASE_URL for a compatible endpoint.");
    }
}
