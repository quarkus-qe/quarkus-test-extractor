package io.quarkus.test.extractor.project.utils;

import io.quarkus.test.extractor.project.builder.Project;
import io.quarkus.test.extractor.project.result.ParentProject;
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
    private static final String QUARKUS_BUILD_PARENT = "quarkus-build-parent";
    private static final String QUARKUS_DOCUMENTATION = "quarkus-documentation";

    private PluginUtils() {
    }

    public static boolean isExtensionTestModule(String relativePath) {
        return relativePath.startsWith(EXTENSIONS + File.separator)
                && (relativePath.endsWith(File.separator + DEPLOYMENT)
        || isExtensionsSupplementaryModule(relativePath));
    }

    /**
     * @return true if it does not contain tests, but is required by other extension modules during testing
     */
    public static boolean isExtensionsSupplementaryModule(String relativePath) {
        return relativePath.contains(ParentProject.QUARKUS_ARC_TEST_SUPPLEMENT)
                || relativePath.contains(ParentProject.QUARKUS_SECURITY_TEST_UTILS);
    }

    public static boolean isDeploymentArtifact(Dependency dependency) {
        return isDeploymentArtifact(dependency.getArtifactId());
    }

    public static boolean isDeploymentArtifact(String artifactId) {
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

    public static boolean isQuarkusBuildParent(Project project) {
        return QUARKUS_BUILD_PARENT.equals(project.artifactId());
    }

    public static boolean isQuarkusParentPomProject(Project project) {
        return "quarkus-parent".equalsIgnoreCase(project.artifactId());
    }

    public static boolean isLastModule(String projectArtifactId) {
        // TODO: this is ugly and hacky, docs module is the last now, but if this changes this will result in tests loss
        return QUARKUS_DOCUMENTATION.equals(projectArtifactId);
    }

    public static Path getTargetProjectDirPath(Project project) {
        return TARGET_DIR.resolve(project.targetRelativePath());
    }
}
