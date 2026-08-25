package com.agent4j.coding.extension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceConfigurationError;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtensionLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsProgrammaticExtensionsBeforeApplicationClasspathProviders() throws Exception {
        try (URLClassLoader classLoader = serviceClassLoader(DiscoveredExtension.class)) {
            List<AgentExtension> extensions = ExtensionLoader.builder()
                    .addExtension(extension("programmatic"))
                    .applicationClassLoader(classLoader)
                    .build()
                    .load();

            assertThat(extensions).extracting(AgentExtension::name)
                    .containsExactly("programmatic", "discovered");
        }
    }

    @Test
    void disabledLoaderDoesNotLoadProgrammaticOrClasspathExtensions() throws Exception {
        try (URLClassLoader classLoader = serviceClassLoader(DiscoveredExtension.class)) {
            List<AgentExtension> extensions = ExtensionLoader.builder()
                    .enabled(false)
                    .addExtension(extension("programmatic"))
                    .applicationClassLoader(classLoader)
                    .build()
                    .load();

            assertThat(extensions).isEmpty();
        }
    }

    @Test
    void missingServiceDescriptorLeavesProgrammaticExtensionsAvailable() throws Exception {
        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, getClass().getClassLoader())) {
            List<AgentExtension> extensions = ExtensionLoader.builder()
                    .addExtension(extension("programmatic"))
                    .applicationClassLoader(classLoader)
                    .build()
                    .load();

            assertThat(extensions).extracting(AgentExtension::name).containsExactly("programmatic");
        }
    }

    @Test
    void rejectsDuplicateProgrammaticAndClasspathExtensionNames() throws Exception {
        try (URLClassLoader classLoader = serviceClassLoader(DiscoveredExtension.class)) {
            assertThatThrownBy(() -> ExtensionLoader.builder()
                    .addExtension(extension("discovered"))
                    .applicationClassLoader(classLoader)
                    .build()
                    .load())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("duplicate extension name: discovered");
        }
    }

    @Test
    void wrapsFailingClasspathProviderAsAnExtensionLoadException() throws Exception {
        try (URLClassLoader classLoader = serviceClassLoader(FailingExtension.class)) {
            assertThatThrownBy(() -> ExtensionLoader.builder()
                    .applicationClassLoader(classLoader)
                    .build()
                    .load())
                    .isInstanceOf(ExtensionLoadException.class)
                    .hasMessage("failed to load application-classpath extension provider")
                    .hasCauseInstanceOf(ServiceConfigurationError.class);
        }
    }

    @Test
    void doesNotActivateProjectScopedExtensionsForAnUntrustedProject() {
        List<AgentExtension> extensions = ExtensionLoader.builder()
                .addExtension(extension("application"))
                .addExtension(projectExtension("project"))
                .build()
                .load(new ExtensionContext(tempDir, null, false));

        assertThat(extensions).extracting(AgentExtension::name).containsExactly("application");
    }

    @Test
    void activatesProjectScopedExtensionsForATrustedProject() {
        List<AgentExtension> extensions = ExtensionLoader.builder()
                .addExtension(projectExtension("project"))
                .build()
                .load(new ExtensionContext(tempDir, null, true));

        assertThat(extensions).extracting(AgentExtension::name).containsExactly("project");
    }

    private URLClassLoader serviceClassLoader(Class<? extends AgentExtension> provider) throws Exception {
        Path serviceFile = tempDir.resolve("META-INF/services/" + AgentExtension.class.getName());
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, provider.getName());
        return new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, getClass().getClassLoader());
    }

    private static AgentExtension extension(String name) {
        return () -> name;
    }

    private static AgentExtension projectExtension(String name) {
        return new AgentExtension() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public ExtensionScope scope() {
                return ExtensionScope.PROJECT;
            }

            @Override
            public boolean requiresProjectTrust() {
                return true;
            }
        };
    }

    public static final class DiscoveredExtension implements AgentExtension {
        @Override
        public String name() {
            return "discovered";
        }
    }

    public static final class FailingExtension implements AgentExtension {
        public FailingExtension() {
            throw new IllegalStateException("provider failure");
        }

        @Override
        public String name() {
            return "failing";
        }
    }
}
