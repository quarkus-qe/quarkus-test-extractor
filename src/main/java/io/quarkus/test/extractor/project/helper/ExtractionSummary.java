package io.quarkus.test.extractor.project.helper;

import io.quarkus.test.extractor.project.builder.Project;
import io.quarkus.test.extractor.project.utils.MavenUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Repository;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.quarkus.test.extractor.project.utils.MavenUtils.USE_EXTRACTED_PROPERTIES;
import static io.quarkus.test.extractor.project.utils.MavenUtils.getManagementKey;
import static io.quarkus.test.extractor.project.utils.MavenUtils.isNotSurefireOrFailsafePlugin;
import static io.quarkus.test.extractor.project.utils.MavenUtils.isTestJar;
import static io.quarkus.test.extractor.project.utils.PluginUtils.TARGET_DIR;
import static io.quarkus.test.extractor.project.utils.PluginUtils.isLastModule;

public record ExtractionSummary(String projectArtifactId,
                                Map<UnmanagedDependencyKey, Set<Usage>> unmanagedDependencies,
                                Map<RepositoryKey, Set<Usage>> projectSpecificRepositories,
                                Map<RepositoryKey, Set<Usage>> projectSpecificPluginRepositories,
                                Map<ProjectSpecificPlugin, Set<Usage>> projectSpecificPlugins,
                                Set<DependencyManagementKey> projectSpecificDependencyManagements)
        implements Serializable {

    record ProjectSpecificPlugin(String pluginName, String pluginVersion) implements Serializable {}
    record UnmanagedDependencyKey(String managementKey, String version, boolean isTestJar)
            implements Serializable { }
    record RepositoryKey(String name, String id, String url) implements Serializable {}
    record Usage(String projectId, String relativePath) implements Serializable {}
    record DependencyManagementKey(Set<String> managementKeys, Usage usage) implements Serializable {}

    private static final String PARTIAL_EXTRACTION_SUMMARIES_DIR_NAME = "partial-extraction-summaries";
    private static final String EXTRACTION_SUMMARY_FILE_NAME = "extraction-summary";

    private ExtractionSummary(String projectArtifactId) {
        this(projectArtifactId, new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashSet<>());
    }

    public static ExtractionSummary of(String projectArtifactId) {
        if (isLastModule(projectArtifactId)) {
            return createOverallExtractionSummary(projectArtifactId);
        } else {
            return new ExtractionSummary(projectArtifactId);
        }
    }

    public void addBuildPlugin(Plugin plugin, Project project) {
        String artifactId = plugin.getArtifactId();
        if (isNotSurefireOrFailsafePlugin(artifactId)) {
            String pluginVersion = plugin.getVersion().contains(USE_EXTRACTED_PROPERTIES)
                    ? plugin.getVersion().replace(USE_EXTRACTED_PROPERTIES, "")
                    : plugin.getVersion();
            var projectSpecificPlugin = new ProjectSpecificPlugin(artifactId, pluginVersion);
            var usage = new Usage(project.artifactId(), project.targetRelativePath());
            projectSpecificPlugins.computeIfAbsent(projectSpecificPlugin, k -> new HashSet<>()).add(usage);
        }
    }

    public void addNotManagedDependency(Dependency dependency, Project project, String version) {
        var usage = new Usage(project.artifactId(), project.targetRelativePath());
        var key = new UnmanagedDependencyKey(getManagementKey(dependency), version, isTestJar(dependency));
        unmanagedDependencies.computeIfAbsent(key, k -> new HashSet<>()).add(usage);
    }

    public void addNotManagedDependency(Dependency dependency, Project project) {
        addNotManagedDependency(dependency, project, dependency.getVersion());
    }

    public void createAndStoreFinalSummary() {
        // create final extraction summary for all the projects
        try {
            Files.writeString(getSummaryPath(), createSummary(), StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create test extraction summary", e);
        }
    }

    public void createAndStorePartialSummary() {
        if (summaryHasContent()) {
            storePartialSummaryToFileSystem(projectArtifactId, this);
        }
    }

    private boolean summaryHasContent() {
        return !unmanagedDependencies.isEmpty() || !projectSpecificRepositories.isEmpty()
                || !projectSpecificPluginRepositories.isEmpty() || !projectSpecificPlugins.isEmpty()
                || !projectSpecificDependencyManagements.isEmpty();
    }

    public void addRepository(Repository repository, Project project) {
        var key = new RepositoryKey(repository.getName(), repository.getId(), repository.getUrl());
        var usage = new Usage(project.artifactId(), project.targetRelativePath());
        projectSpecificRepositories.computeIfAbsent(key, k -> new HashSet<>()).add(usage);
    }

    public void addPluginRepository(Repository repository, Project project) {
        var key = new RepositoryKey(repository.getName(), repository.getId(), repository.getUrl());
        var usage = new Usage(project.artifactId(), project.targetRelativePath());
        projectSpecificPluginRepositories.computeIfAbsent(key, k -> new HashSet<>()).add(usage);
    }

    public void addProjectWithDependencyManagement(DependencyManagement dependencyManagement, Project project) {
        var managementKeys = dependencyManagement.getDependencies().stream().map(MavenUtils::getManagementKey)
                .collect(Collectors.toUnmodifiableSet());
        var usage = new Usage(project.artifactId(), project.targetRelativePath());
        DependencyManagementKey key = new DependencyManagementKey(managementKeys, usage);
        projectSpecificDependencyManagements.add(key);
    }

    private String createSummary() {
        String unmanagedDependencies = unmanagedDependencies()
                .entrySet()
                .stream()
                .map(ExtractionSummary::foundUnmanagedDependency)
                .collect(Collectors.joining());
        String projectRepositories = projectSpecificRepositories
                .entrySet()
                .stream()
                .map(ExtractionSummary::foundProjectRepository)
                .collect(Collectors.joining());
        String projectBuildPlugins = projectSpecificPlugins
                .entrySet()
                .stream()
                .map(ExtractionSummary::foundBuildPlugin)
                .collect(Collectors.joining());
        String projectPluginRepositories = projectSpecificPluginRepositories
                .entrySet()
                .stream()
                .map(ExtractionSummary::foundProjectRepository)
                .collect(Collectors.joining());
        if (projectPluginRepositories.isEmpty()) {
            projectPluginRepositories = "none found";
        }
        String projectsWithOwnDepManagement = projectSpecificDependencyManagements
                .stream()
                .map(ExtractionSummary::foundDependencyManagement)
                .collect(Collectors.joining());
        if (projectsWithOwnDepManagement.isEmpty()) {
            projectsWithOwnDepManagement = "none found";
        }
        return """
                Test extraction summary:
                
                === UnmanagedDependencies:
                %s

                === Projects that have configured a repository in their POM:
                %s
                
                === Projects that have configured a plugin repository in their POM:
                %s
                
                === Projects that have their own dependency managements
                %s
                
                === Leaving aside SureFire and Failsafe plugins, following projects contains their own build plugins
                    (please note there probably nothing wrong about that, just revise their setup like
                     version or properties in relation to RHBQ, if not relevant, ignore them;
                     many plugins are specified in profiles and these are not listed here):
                %s
                """.formatted(unmanagedDependencies, projectRepositories, projectPluginRepositories,
                projectsWithOwnDepManagement, projectBuildPlugins);
    }

    private static void storePartialSummaryToFileSystem(String projectArtifactId, ExtractionSummary summary) {
        if (!Files.exists(getExtractionSummariesDir())) {
            getExtractionSummariesDir().toFile().mkdirs();
        }

        FileOutputStream fileOutputStream;
        try {
            File partialSummaryFile = getPartialSummaryPath(projectArtifactId).toFile();
            partialSummaryFile.createNewFile();
            fileOutputStream = new FileOutputStream(partialSummaryFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create file for summary " + projectArtifactId, e);
        }
        try(var objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
            objectOutputStream.writeObject(summary);
            objectOutputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file for summary " + projectArtifactId, e);
        }
    }

    private static String foundDependencyManagement(DependencyManagementKey dependencyManagementKey) {
        return """
                - project '%s' located at '%s' manages following dependencies: %s
                """.formatted(dependencyManagementKey.usage.projectId, dependencyManagementKey.usage.relativePath,
                dependencyManagementKey.managementKeys);
    }

    private static String foundUnmanagedDependency(Map.Entry<UnmanagedDependencyKey, Set<Usage>> e) {
        var key = e.getKey();
        String managementKey = key.managementKey;
        String version = key.version == null ? "<<no version>>" : key.version;
        String dependency = managementKey + (key.isTestJar ? " (test-jar type)" : "");
        return """
                - Dependency '%s' is not managed by Quarkus BOM, going to use '%s' version, this dependency is used at:
                %s
                """.formatted(dependency, version, buildUsagesReport(e.getValue()));
    }

    private static String foundProjectRepository(Map.Entry<RepositoryKey, Set<Usage>> e) {
        String repositoryName = e.getKey().name == null ? "<<no repository name found>>" : e.getKey().name;
        String repositoryId = e.getKey().id;
        String repositoryUrl = e.getKey().url;
        return """
                - Repository '%s' (with id '%s' and URL '%s') was declared in POM of following projects:
                %s
                """.formatted(repositoryName, repositoryId, repositoryUrl, buildUsagesReport(e.getValue()));
    }

    private static String foundBuildPlugin(Map.Entry<ProjectSpecificPlugin, Set<Usage>> e) {
        String pluginName = e.getKey().pluginName == null ? "<<no plugin name found>>" : e.getKey().pluginName;
        String pluginVersion = e.getKey().pluginVersion;
        return """
                - Build plugin '%s' (with version '%s') was declared in POM of following projects:
                %s
                """.formatted(pluginName, pluginVersion, buildUsagesReport(e.getValue()));
    }

    private static String buildUsagesReport(Set<Usage> e) {
        StringBuilder usages = new StringBuilder();
        for (Usage usage : e) {
            usages
                    .append("  - project '")
                    .append(usage.projectId)
                    .append("' located at '")
                    .append(usage.relativePath)
                    .append("'")
                    .append(System.lineSeparator());
        }
        return usages.toString();
    }

    private static Path getSummaryPath() {
        return TARGET_DIR.resolve(EXTRACTION_SUMMARY_FILE_NAME);
    }

    private static Path getPartialSummaryPath(String projectArtifactId) {
        return getExtractionSummariesDir().resolve(EXTRACTION_SUMMARY_FILE_NAME + "-" + projectArtifactId);
    }

    private static Path getExtractionSummariesDir() {
        return TARGET_DIR.resolve(PARTIAL_EXTRACTION_SUMMARIES_DIR_NAME);
    }

    private static Stream<Path> listPartialExtractionSummaries() {
        try {
            return Files.list(getExtractionSummariesDir());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load partial extraction summaries", e);
        }
    }

    private static ExtractionSummary loadPartialSummary(Path path) {
        FileInputStream fileInputStream;
        try {
            fileInputStream = new FileInputStream(path.toFile());
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Failed to open input stream to " + path, e);
        }
        try(var objectInputStream = new ObjectInputStream(fileInputStream)) {
            return (ExtractionSummary) objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to deserialize partial extraction summary from " + path, e);
        }
    }

    private static ExtractionSummary createOverallExtractionSummary(String projectArtifactId) {
        Map<UnmanagedDependencyKey, Set<Usage>> unmanagedDependencies = new HashMap<>();
        Map<RepositoryKey, Set<Usage>> projectSpecificRepositories = new HashMap<>();
        Map<RepositoryKey, Set<Usage>> projectSpecificPluginRepositories = new HashMap<>();
        Map<ProjectSpecificPlugin, Set<Usage>> projectSpecificPlugins = new HashMap<>();
        Set<DependencyManagementKey> projectSpecificDependencyManagements = new HashSet<>();
        listPartialExtractionSummaries().map(ExtractionSummary::loadPartialSummary).forEach(summary -> {
            unmanagedDependencies.putAll(summary.unmanagedDependencies);
            projectSpecificRepositories.putAll(summary.projectSpecificRepositories);
            projectSpecificPluginRepositories.putAll(summary.projectSpecificPluginRepositories);
            projectSpecificPlugins.putAll(summary.projectSpecificPlugins);
            projectSpecificDependencyManagements.addAll(summary.projectSpecificDependencyManagements);
        });
        return new ExtractionSummary(projectArtifactId, unmanagedDependencies, projectSpecificRepositories,
                projectSpecificPluginRepositories, projectSpecificPlugins, projectSpecificDependencyManagements);
    }
}
