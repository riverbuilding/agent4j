package com.agent4j.cli;

import com.agent4j.coding.sdk.AuthStatus;
import com.agent4j.coding.sdk.LoginService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.PrintWriter;
import java.util.Optional;
import java.util.concurrent.Callable;

final class AuthCommands {
    private AuthCommands() {
    }

    abstract static class BaseCommand implements Callable<Integer> {
        @ParentCommand
        Agent4jRootCommand root;

        @Option(names = "--provider", defaultValue = "openai", description = "Provider ID")
        String providerId;

        final LoginService loginService() throws Exception {
            return root.runtimeFactory().create(root.authRuntimeRequest(providerId)).sessionRuntime().loginService();
        }

        final PrintWriter out() {
            return root.commandSpec().commandLine().getOut();
        }

        final PrintWriter err() {
            return root.commandSpec().commandLine().getErr();
        }

        final int printStatus(AuthStatus status) {
            out().println("provider: " + status.providerId());
            out().println("authenticated: " + status.authenticated());
            out().println("mode: " + status.mode().wireName());
            out().println("expired: " + status.expired());
            status.expiresAt().ifPresent(value -> out().println("expiresAt: " + value));
            out().flush();
            return status.authenticated() && !status.expired() ? 0 : 1;
        }
    }

    @Command(name = "login", description = "Log in using the provider browser flow")
    static final class LoginCommand extends BaseCommand {
        @Override
        public Integer call() throws Exception {
            if (!"openai".equals(providerId)) {
                throw new IllegalArgumentException("browser subscription login is currently supported only for openai");
            }
            AuthStatus status = loginService().loginOpenAiSubscription();
            return printStatus(status);
        }
    }

    @Command(name = "logout", description = "Remove saved provider credentials")
    static final class LogoutCommand extends BaseCommand {
        @Override
        public Integer call() throws Exception {
            boolean removed = loginService().logout(providerId);
            out().println(removed ? "Logged out: " + providerId : "No saved credentials: " + providerId);
            out().flush();
            return 0;
        }
    }

    @Command(name = "auth-status", description = "Show provider authentication status")
    static final class StatusCommand extends BaseCommand {
        @Override
        public Integer call() throws Exception {
            return printStatus(loginService().status(providerId));
        }
    }

    @Command(name = "refresh", description = "Refresh provider credentials")
    static final class RefreshCommand extends BaseCommand {
        @Override
        public Integer call() throws Exception {
            loginService().refreshAuth(providerId);
            return printStatus(loginService().status(providerId));
        }
    }
}
