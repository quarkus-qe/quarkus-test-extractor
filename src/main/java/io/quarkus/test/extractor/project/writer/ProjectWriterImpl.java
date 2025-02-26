package io.quarkus.test.extractor.project.writer;

import io.quarkus.test.extractor.project.builder.Project;
import io.quarkus.test.extractor.project.helper.ExtractionSummary;
import io.quarkus.test.extractor.project.helper.QuarkusBuildParent;
import io.quarkus.test.extractor.project.result.ParentProject;
import io.quarkus.test.extractor.project.result.TestModuleProject;
import io.quarkus.test.extractor.project.utils.MavenUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.quarkus.test.extractor.project.result.ParentProject.copyAsIs;
import static io.quarkus.test.extractor.project.utils.MavenUtils.computeRelativePath;
import static io.quarkus.test.extractor.project.utils.PluginUtils.EXTENSIONS;
import static io.quarkus.test.extractor.project.utils.PluginUtils.INTEGRATION_TESTS;
import static io.quarkus.test.extractor.project.utils.PluginUtils.TARGET_DIR;

final class ProjectWriterImpl implements ProjectWriter {

    private static final String QUARKUS_BUILD_PARENT = "quarkus-build-parent";
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
        } else if (copyAsIs(project)) {
            copyWithoutChanges(project);
        } else if (project.containsTests()) {
            createTestModuleFrom(project);
        }

        if (isLastModule(project)) {
            ParentProject.writeTo(TARGET_DIR);
            ExtractionSummary.createAndStoreSummary();
        }
    }

    private static void copyWithoutChanges(Project project) {
        Model model = project.originalModel();
        if (project.isDirectSubModule()) {
            Parent parent = model.getParent();
            parent.setGroupId("io.quarkus");
            parent.setArtifactId("quarkus-main-tests");
            parent.setVersion(project.version());
            parent.setRelativePath(computeRelativePath(project));
        }
        createMavenModule(project, model, getModelPath(project));
    }

    private static Path getModelPath(Project project) {
        return TARGET_DIR.resolve(project.targetRelativePath());
    }

    private void copyQuarkusBuildParentToOurParentProject(Project project) {
        quarkusBuildParentDetected = true;
        ParentProject.addProperties(project.properties());
        ParentProject.setQuarkusVersion(project.version());
        QuarkusBuildParent.rememberDependencyManagement(project.dependencyManagement());
    }

    private boolean isQuarkusBuildParent(Project project) {
        return !quarkusBuildParentDetected && QUARKUS_BUILD_PARENT.equals(project.artifactId());
    }

    private void createTestModuleFrom(Project project) {
        Model testModel = TestModuleProject.create(project);
        Path testModelPath = getModelPath(project);
        createMavenModule(project, testModel, testModelPath);

        // FIXME: drop following
        // create pom
        // - resolve all dependencies
        //   - resolve non-platform dependencies and put them to the parent properties && dependency management (add comment that lists which modules need it)
        // - resolve properties like those used by surefire etc. and probably copy properties as well? unclear!
        // - copy plugins
        // - copy dependencies:
        //    - differs between ITs and extension tests
        // -
        // copy files


        // FIXME: how to handle submodules???

    }

    private static void createMavenModule(Project project, Model testModel, Path testModelPath) {
        createModuleDirectory(project);
        addToParentPomModel(project);
        MavenUtils.writeMavenModel(testModel, testModelPath);
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

    private static void createModuleDirectory(Project project) {
        try {
            Files.createDirectories(TARGET_DIR.resolve(project.targetRelativePath()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create test module directory", e);
        }
    }
}
