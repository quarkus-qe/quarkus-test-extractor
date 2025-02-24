package io.quarkus.test.extractor.project.writer;

import io.quarkus.test.extractor.project.builder.Project;
import io.quarkus.test.extractor.project.result.ParentProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.quarkus.test.extractor.utils.ConstantUtils.EXTENSIONS;
import static io.quarkus.test.extractor.utils.ConstantUtils.INTEGRATION_TESTS;
import static io.quarkus.test.extractor.utils.ConstantUtils.WRITE_TO;

final class ProjectWriterImpl implements ProjectWriter {

    private static final String QUARKUS_BUILD_PARENT = "quarkus-build-parent";
    private static final Path TARGET_DIR = Path.of(System.getProperty(WRITE_TO));
    private static final Path EXTENSION_MODULES_PATH = TARGET_DIR.resolve(EXTENSIONS);
    private static final Path IT_MODULES_PATH = TARGET_DIR.resolve(INTEGRATION_TESTS);
    private static final ProjectWriter INSTANCE = new ProjectWriterImpl();
    private final AtomicBoolean isFirstModule;
    private volatile boolean quarkusBuildParentDetected;

    private ProjectWriterImpl() {
        this.isFirstModule = new AtomicBoolean(true);
        this.quarkusBuildParentDetected = false;
    }

    static ProjectWriter getInstance() {
        return INSTANCE;
    }

    @Override
    public void writeProject(Project project) {
        if (isFirstModule()) {
            createDirectoryStructure();
        }

        if (isQuarkusBuildParent(project)) {
            copyQuarkusBuildParentToOurParentProject(project);
        } else if (project.containsTests()) {
            createTestModuleFrom(project);
        }

        if (isLastModule(project)) {
            ParentProject.writeTo(TARGET_DIR);
        }
    }

    private void copyQuarkusBuildParentToOurParentProject(Project project) {
        quarkusBuildParentDetected = true;
        ParentProject.addProperties(project.properties());
        ParentProject.setQuarkusVersion(project.version());
    }

    private boolean isQuarkusBuildParent(Project project) {
        return !quarkusBuildParentDetected && QUARKUS_BUILD_PARENT.equals(project.artifactId());
    }

    private void createTestModuleFrom(Project project) {
        createTestModuleDirectory(project);
        addToParentPomModel(project);
        // create pom
        // - resolve all dependencies
        //   - resolve non-platform dependencies and put them to the parent properties && dependency management (add comment that lists which modules need it)
        // - resolve properties like those used by surefire etc. and probably copy properties as well? unclear!
        // - copy plugins
        // - copy dependencies:
        //    - differs between ITs and extension tests
        // -
        // copy files
        // FIXME: DROP ME!
        project.properties();

        // FIXME: how to handle submodules???

    }

    private boolean isFirstModule() {
        return isFirstModule.compareAndSet(true, false);
    }

    private static void addToParentPomModel(Project project) {
        if (project.isDirectSubModule()) {
            ParentProject.addTestModule(project.targetRelativePath(), project.targetProfileName());
        }
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
