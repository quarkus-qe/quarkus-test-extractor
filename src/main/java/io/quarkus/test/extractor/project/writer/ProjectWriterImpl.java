package io.quarkus.test.extractor.project.writer;

import io.quarkus.test.extractor.project.builder.Project;
import io.quarkus.test.extractor.project.result.ParentProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

final class ProjectWriterImpl implements ProjectWriter {

    private static final String EXTENSIONS = "extensions";
    private static final String INTEGRATION_TESTS = "integration-tests";
    private static final Path TARGET_DIR = Path.of(System.getProperty("write-to"));
    private static final Path EXTENSION_MODULES_PATH = TARGET_DIR.resolve(EXTENSIONS);
    private static final Path IT_MODULES_PATH = TARGET_DIR.resolve(INTEGRATION_TESTS);
    private static final ProjectWriter INSTANCE = new ProjectWriterImpl();
    private final AtomicInteger counter;

    private ProjectWriterImpl() {
        this.counter = new AtomicInteger(0);
    }

    static ProjectWriter getInstance() {
        return INSTANCE;
    }

    @Override
    public void writeProject(Project project) {
        if (isFirstModule()) {
            createDirectoryStructure();
        }

        if (containsTests(project)) {
            createTestModuleFrom(project);
        }

        if (isLastModule(project)) {
            ParentProject.writeTo(TARGET_DIR);
        }
    }

    private void createTestModuleFrom(Project project) {
        createTestModuleDirectory(project);
        addToParentPomModel(project);
        // FIXME: how to handle submodules???
    }

    private boolean isFirstModule() {
        return counter.incrementAndGet() == 1;
    }

    private static boolean containsTests(Project project) {
        return project.relativePath().startsWith(EXTENSIONS)
                || project.relativePath().startsWith(INTEGRATION_TESTS);
    }

    private static void addToParentPomModel(Project project) {
        if (project.isDirectSubModule()) {
            ParentProject.addTestModule(project.relativePath(), getProfileName(project));
        }
    }

    private static String getProfileName(Project project) {
        return project.relativePath().startsWith(EXTENSIONS) ? EXTENSIONS : INTEGRATION_TESTS;
    }

    private static void createDirectoryStructure() {
        Objects.requireNonNull(TARGET_DIR, "Please specify target directory with '-Dwrite-to=<path>'");
        try {
            Files.createDirectories(TARGET_DIR);
            Files.createDirectories(EXTENSION_MODULES_PATH);
            Files.createDirectories(IT_MODULES_PATH);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create target directory structure", e);
        }
    }

    private static boolean isLastModule(Project project) {
        // TODO: this is ugly and hacky, docs module is the last now, but if this changes this will result in tests loss
        return "quarkus-documentation".equals(project.artifactId());
    }

    private static void createTestModuleDirectory(Project project) {
        try {
            Files.createDirectories(TARGET_DIR.resolve(project.relativePath()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create test module directory", e);
        }
    }
}
