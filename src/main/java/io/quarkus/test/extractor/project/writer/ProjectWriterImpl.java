package io.quarkus.test.extractor.project.writer;

import io.quarkus.test.extractor.project.builder.Project;
import io.quarkus.test.extractor.project.helper.ExtractionSummary;
import io.quarkus.test.extractor.project.helper.QuarkusBuildParent;
import io.quarkus.test.extractor.project.helper.QuarkusParentPom;
import io.quarkus.test.extractor.project.result.ParentProject;
import io.quarkus.test.extractor.project.result.TestModuleProject;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static io.quarkus.test.extractor.project.result.ParentProject.copyAsIs;
import static io.quarkus.test.extractor.project.utils.MavenUtils.computeRelativePath;
import static io.quarkus.test.extractor.project.utils.MavenUtils.writeMavenModel;
import static io.quarkus.test.extractor.project.utils.PluginUtils.EXTENSIONS;
import static io.quarkus.test.extractor.project.utils.PluginUtils.INTEGRATION_TESTS;
import static io.quarkus.test.extractor.project.utils.PluginUtils.TARGET_DIR;
import static io.quarkus.test.extractor.project.utils.PluginUtils.isLastModule;
import static io.quarkus.test.extractor.project.utils.PluginUtils.isQuarkusBuildParent;
import static io.quarkus.test.extractor.project.utils.PluginUtils.isQuarkusParentPomProject;

final class ProjectWriterImpl implements ProjectWriter {

    private static final Path EXTENSION_MODULES_PATH = TARGET_DIR.resolve(EXTENSIONS);
    private static final Path IT_MODULES_PATH = TARGET_DIR.resolve(INTEGRATION_TESTS);

    private final ExtractionSummary extractionSummary;

    ProjectWriterImpl(ExtractionSummary extractionSummary) {
        this.extractionSummary = extractionSummary;
    }

    @Override
    public void writeProject(Project project) {
        if (isFirstModule()) {
            createDirectoryStructure();
        }

        if (isQuarkusBuildParent(project)) {
            copyQuarkusBuildParentToOurParentProject(project);
        } else if (isQuarkusParentPomProject(project)) {
            QuarkusParentPom.collectPluginVersions(project);
        } else if (copyAsIs(project)) {
            copyWholeProject(project);
        } else if (project.containsTests()) {
            createTestModuleFrom(project);
        }

        if (isLastModule(project.artifactId())) {
            ParentProject.writeTo(TARGET_DIR);
            extractionSummary.createAndStoreFinalSummary();
        } else {
            extractionSummary.createAndStorePartialSummary();
        }
    }

    private static void copyWholeProject(Project project) {
        Model model = project.originalModel();
        if (project.isDirectSubModule()) {
            Parent parent = model.getParent();
            parent.setGroupId("io.quarkus");
            parent.setArtifactId("quarkus-main-tests");
            parent.setVersion(project.version());
            parent.setRelativePath(computeRelativePath(project));
        }
        createMavenModule(project, model, getModelPath(project));
        // we copy the whole project, so we need to manage it so that it is found
        // if some test module needs it
        ParentProject.addManagedProject(project);
    }

    private static Path getModelPath(Project project) {
        return TARGET_DIR.resolve(project.targetRelativePath());
    }

    private static void copyQuarkusBuildParentToOurParentProject(Project project) {
        ParentProject.addProperties(project.properties());
        ParentProject.setQuarkusVersion(project.version());
        QuarkusBuildParent.rememberDependencyManagement(project.dependencyManagement());
    }

    private static void createTestModuleFrom(Project project) {
        Model testModel = TestModuleProject.create(project);
        Path testModelPath = getModelPath(project);
        createMavenModule(project, testModel, testModelPath);
    }

    private static void createMavenModule(Project project, Model testModel, Path testModelPath) {
        createModuleDirectory(project);
        addToParentPomModel(project);
        writeMavenModel(testModel, testModelPath);
    }

    private static void addToParentPomModel(Project project) {
        if (project.isDirectSubModule()) {
            ParentProject.addTestModule(project.targetRelativePath(), project.targetProfileName());
        }
    }

    private static boolean isFirstModule() {
        return !Files.exists(TARGET_DIR) || !Files.exists(EXTENSION_MODULES_PATH)
                || !Files.exists(IT_MODULES_PATH);
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

    private static void createModuleDirectory(Project project) {
        try {
            Files.createDirectories(TARGET_DIR.resolve(project.targetRelativePath()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create test module directory", e);
        }
    }
}
