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
        try (LiveExampleRuntime runtime = LiveExampleRuntime.open()) {
            System.out.println("Live OpenAI example runtime is ready.");
            System.out.println("Model: " + runtime.model().providerId() + "/" + runtime.model().modelId());
            System.out.println("Workspace: " + runtime.workspace());
            System.out.println("Session directory: " + runtime.sessionDirectory());
            System.out.println("Max output tokens: " + runtime.maxOutputTokens());
            System.out.println("Max tool rounds: " + runtime.maxToolRounds());
            System.out.println("Temporary workspace: " + runtime.temporaryWorkspace());
            System.out.println("Temporary session directory: " + runtime.temporarySessionDirectory());
            System.out.println("No API request was sent by this preflight check.");
        }
    }

    private static void usage() {
        System.out.println("Usage: LiveExamplePreflight [help]");
        System.out.println("Set OPENAI_API_KEY and AGENT4J_OPENAI_MODEL before running the preflight check.");
    }
}
