package com.agent4j.coding.extension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

/** Loads explicitly supplied and application-classpath Java extensions. */
public final class ExtensionLoader {
    private final boolean enabled;
    private final ClassLoader applicationClassLoader;
    private final List<AgentExtension> programmaticExtensions;

    private ExtensionLoader(Builder builder) {
        enabled = builder.enabled;
        applicationClassLoader = builder.applicationClassLoader;
        programmaticExtensions = List.copyOf(builder.programmaticExtensions);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Loads extensions in programmatic-registration order followed by application-classpath order.
     *
     * <p>Disabled loading returns no extensions. A missing service descriptor is not an error; an
     * invalid provider or duplicate extension name is a startup configuration error.</p>
     */
    public List<AgentExtension> load() {
        if (!enabled) {
            return List.of();
        }

        List<AgentExtension> extensions = new ArrayList<>(programmaticExtensions);
        Iterator<AgentExtension> providers = ServiceLoader.load(AgentExtension.class, applicationClassLoader).iterator();
        try {
            while (providers.hasNext()) {
                extensions.add(providers.next());
            }
        } catch (ServiceConfigurationError error) {
            throw new ExtensionLoadException("failed to load application-classpath extension provider", error);
        }
        validateDistinctNames(extensions);
        return List.copyOf(extensions);
    }

    private static void validateDistinctNames(List<AgentExtension> extensions) {
        Set<String> names = new HashSet<>();
        for (AgentExtension extension : extensions) {
            Objects.requireNonNull(extension, "extensions must not contain null");
            String name = Objects.requireNonNull(extension.name(), "extension name");
            if (name.isBlank()) {
                throw new IllegalArgumentException("extension name must not be blank");
            }
            if (!names.add(name)) {
                throw new IllegalArgumentException("duplicate extension name: " + name);
            }
        }
    }

    /** Builder for application embedding and test-only extension configuration. */
    public static final class Builder {
        private boolean enabled = true;
        private ClassLoader applicationClassLoader = ClassLoader.getSystemClassLoader();
        private final List<AgentExtension> programmaticExtensions = new ArrayList<>();

        private Builder() {
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /** Overrides the application class loader for embedded applications and tests. */
        public Builder applicationClassLoader(ClassLoader applicationClassLoader) {
            this.applicationClassLoader = Objects.requireNonNull(applicationClassLoader, "applicationClassLoader");
            return this;
        }

        /** Adds a trusted extension supplied directly by embedding application code. */
        public Builder addExtension(AgentExtension extension) {
            programmaticExtensions.add(Objects.requireNonNull(extension, "extension"));
            return this;
        }

        public ExtensionLoader build() {
            return new ExtensionLoader(this);
        }
    }
}
