package io.quarkus.test.extractor.project.utils;

import org.apache.maven.model.Dependency;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public final class PluginUtils {

    /**
     * Modules where we look for tests.
     */
    public static final String EXTENSIONS = "extensions";
    public static final String INTEGRATION_TESTS = "integration-tests";
    /**
     * System property name that determines where to write generated projects.
     */
    private static final String WRITE_TO = "write-to";
    public static final Path TARGET_DIR = Path.of(requireNonNull(System.getProperty(WRITE_TO),
            "Please specify target directory with '-Dwrite-to=<path>'"));
    private static final String DEPLOYMENT = "deployment";
    private static final String DEPLOYMENT_POSTFIX = "-" + DEPLOYMENT;
    private static final String DEPLOYMENT_NAME_POSTFIX = " - Deployment";
    /**
     * Prefix we add to the extension modules that represents extension tests.
     * This way, we avoid conflicts with actual extensions.
     */
    private static final String TESTS_PREFIX = "tests-";

    private PluginUtils() {
    }

    public static boolean isExtensionDeploymentModule(String relativePath) {
        return relativePath.startsWith(EXTENSIONS + File.separator)
                && relativePath.endsWith(File.separator + DEPLOYMENT);
    }

    public static boolean isDeploymentArtifact(Dependency dependency) {
        String artifactId = dependency.getArtifactId();
        return artifactId != null && artifactId.endsWith(DEPLOYMENT_POSTFIX);
    }

    public static String dropDeploymentPostfix(String text) {
        text = text.trim();
        if (text.endsWith(DEPLOYMENT_POSTFIX)) {
            return text.substring(0, text.length() - DEPLOYMENT_POSTFIX.length());
        }
        if (text.endsWith(DEPLOYMENT_NAME_POSTFIX)) {
            return text.substring(0, text.length() - DEPLOYMENT_NAME_POSTFIX.length()).trim();
        }
        return text;
    }

    public static void createDirectoryStructureIfNotExists() {
        try {
            Files.createDirectories(TARGET_DIR);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create target directory", e);
        }
    }

    public static String prefixWithTests(String artifactId) {
        return TESTS_PREFIX + artifactId;
    }
}
