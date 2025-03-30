package io.quarkus.test.extractor.project.writer;

import io.quarkus.test.extractor.project.builder.Project;
import io.quarkus.test.extractor.project.helper.ExtractionSummary;
import io.quarkus.test.extractor.project.helper.QuarkusBuildParent;
import io.quarkus.test.extractor.project.result.ParentProject;
import io.quarkus.test.extractor.project.result.TestModuleProject;
import io.quarkus.test.extractor.project.utils.MavenUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static io.quarkus.test.extractor.project.helper.QuarkusParentPom.collectPluginVersions;
import static io.quarkus.test.extractor.project.result.ParentProject.copyAsIs;
import static io.quarkus.test.extractor.project.utils.MavenUtils.*;
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
            collectPluginVersions(project);
        } else if (copyAsIs(project)) {
            copyWholeProject(project);
        } else if (project.isTestModule()) {
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
        copyAllFilesInProjectExceptForPom(project);
        Model model = project.originalModel();
        Parent parent = model.getParent();
        parent.setGroupId(TEST_PARENT_GROUP_ID);
        if (project.isDirectSubModule()) {
            parent.setArtifactId("quarkus-main-tests");
            parent.setVersion(project.version());
            parent.setRelativePath(computeRelativePath(project));
        }
        createMavenModule(project, model, getTargetProjectDirPath(project));
        // we copy the whole project, so we need to manage it so that it is found
        // if some test module needs it
        ParentProject.addManagedProject(project);
    }

    private static void copyQuarkusBuildParentToOurParentProject(Project project) {
        ParentProject.addProperties(project.properties());
        ParentProject.setQuarkusVersion(project.version());
        QuarkusBuildParent.rememberDependencyManagement(project.dependencyManagement());
    }

    private static void createTestModuleFrom(Project project) {
        if (project.isIntegrationTestModule()) {
            copyAllFilesInProjectExceptForPom(project);
        } else {
            copyTests(project);
        }
        Model testModel = TestModuleProject.create(project);
        Path testModelTargetPath = getTargetProjectDirPath(project);
        createMavenModule(project, testModel, testModelTargetPath);
    }

    private static void copyAllFilesInProjectExceptForPom(Project project) {
        File sourceProjectDir = project.projectPath().toFile();
        File targetProjectDir = getTargetProjectDirPath(project).toFile();
        copyDirectory(sourceProjectDir, targetProjectDir);
        try {
            Files.deleteIfExists(getTargetProjectDirPath(project).resolve(POM_XML));
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete '%s' project POM file"
                    .formatted(targetProjectDir.getPath()), e);
        }
    }

    private static void copyTests(Project project) {
        Path sourceProjectSrcTestPath = project.projectPath().resolve("src").resolve("test");
        File sourceProjectSrcTestDir = sourceProjectSrcTestPath.toFile();
        Path targetProjectSrcTestPath = getTargetProjectDirPath(project).resolve("src").resolve("test");
        File targetProjectSrcTestDir = targetProjectSrcTestPath.toFile();
        targetProjectSrcTestDir.mkdirs();
        copyDirectory(sourceProjectSrcTestDir, targetProjectSrcTestDir);
    }

    private static void createMavenModule(Project project, Model testModel, Path testModelPath) {
        createModuleDirectory(project);
        addToParentPomModel(project);
        writeMavenModel(testModel, testModelPath);
    }

    private static void addToParentPomModel(Project project) {
        if (project.isDirectSubModule() && project.isTestModule()) {
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
            Files.createDirectories(getTargetProjectDirPath(project));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create test module directory", e);
        }
    }

    private static Path getTargetProjectDirPath(Project project) {
        return TARGET_DIR.resolve(project.targetRelativePath());
    }
}
