package io.quarkus.test.extractor.project.helper;

import io.quarkus.test.extractor.project.builder.Project;
import io.quarkus.test.extractor.project.utils.MavenUtils;
import io.quarkus.test.extractor.project.utils.PluginUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.quarkus.test.extractor.project.utils.MavenUtils.getManagementKey;
import static io.quarkus.test.extractor.project.utils.MavenUtils.isTestJar;

public final class ExtractionSummary {

    private record UnmanagedDependencyKey(String managementKey, String version, boolean isTestJar) {
    }
    private record RepositoryKey(String name, String id, String url) {}
    private record Usage(String projectId, String relativePath) {}
    private record DependencyManagementKey(Set<String> managementKeys, Usage usage) {}
    private static final String EXTRACTION_SUMMARY_FILE_NAME = "extraction-summary";
    private static final Map<UnmanagedDependencyKey, Set<Usage>> UNMANAGED_DEPENDENCIES = new ConcurrentHashMap<>();
    private static final Map<RepositoryKey, Set<Usage>> PROJECT_SPECIFIC_REPOSITORIES = new ConcurrentHashMap<>();
    private static final Map<RepositoryKey, Set<Usage>> PROJECT_SPECIFIC_PLUGIN_REPOSITORIES = new ConcurrentHashMap<>();
    private static final Set<DependencyManagementKey> PROJECT_SPECIFIC_DEPENDING_MANAGEMENTS = new HashSet<>();

    private ExtractionSummary() {
    }

    public static void addNotManagedDependency(Dependency dependency, Project project, String version) {
        var usage = new Usage(project.artifactId(), project.targetRelativePath());
        var key = new UnmanagedDependencyKey(getManagementKey(dependency), version, isTestJar(dependency));
        UNMANAGED_DEPENDENCIES.computeIfAbsent(key, k -> new HashSet<>()).add(usage);
    }

    public static void addNotManagedDependency(Dependency dependency, Project project) {
        addNotManagedDependency(dependency, project, dependency.getVersion());
    }

    public static void createAndStoreSummary() {
        try {
            Files.writeString(getSummaryPath(), createSummary(), StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create test extraction summary", e);
        }
    }

    public static void addRepository(Repository repository, Project project) {
        var key = new RepositoryKey(repository.getName(), repository.getId(), repository.getUrl());
        var usage = new Usage(project.artifactId(), project.targetRelativePath());
        PROJECT_SPECIFIC_REPOSITORIES.computeIfAbsent(key, k -> new HashSet<>()).add(usage);
    }

    public static void addPluginRepository(Repository repository, Project project) {
        var key = new RepositoryKey(repository.getName(), repository.getId(), repository.getUrl());
        var usage = new Usage(project.artifactId(), project.targetRelativePath());
        PROJECT_SPECIFIC_PLUGIN_REPOSITORIES.computeIfAbsent(key, k -> new HashSet<>()).add(usage);
    }

    public static void addProjectWithDependencyManagement(DependencyManagement dependencyManagement, Project project) {
        var managementKeys = dependencyManagement.getDependencies().stream().map(MavenUtils::getManagementKey)
                .collect(Collectors.toUnmodifiableSet());
        var usage = new Usage(project.artifactId(), project.targetRelativePath());
        DependencyManagementKey key = new DependencyManagementKey(managementKeys, usage);
        PROJECT_SPECIFIC_DEPENDING_MANAGEMENTS.add(key);
    }

    private static String createSummary() {
        String unmanagedDependencies = UNMANAGED_DEPENDENCIES
                .entrySet()
                .stream()
                .map(ExtractionSummary::foundUnmanagedDependency)
                .collect(Collectors.joining());
        String projectRepositories = PROJECT_SPECIFIC_REPOSITORIES
                .entrySet()
                .stream()
                .map(ExtractionSummary::foundProjectRepository)
                .collect(Collectors.joining());
        String projectPluginRepositories = PROJECT_SPECIFIC_PLUGIN_REPOSITORIES
                .entrySet()
                .stream()
                .map(ExtractionSummary::foundProjectRepository)
                .collect(Collectors.joining());
        if (projectPluginRepositories.isEmpty()) {
            projectPluginRepositories = "none found";
        }
        String projectsWithOwnDepManagement = PROJECT_SPECIFIC_DEPENDING_MANAGEMENTS
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
                """.formatted(unmanagedDependencies, projectRepositories, projectPluginRepositories,
                projectsWithOwnDepManagement);
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
        StringBuilder usages = new StringBuilder();
        for (Usage usage : e.getValue()) {
            usages
                    .append("  - project '")
                    .append(usage.projectId)
                    .append("' located at '")
                    .append(usage.relativePath)
                    .append("'")
                    .append(System.lineSeparator());
        }
        String dependency = managementKey + (key.isTestJar ? " (test-jar type)" : "");
        return """
                - Dependency '%s' is not managed by Quarkus BOM, going to use '%s' version, this dependency is used at:
                %s
                """.formatted(dependency, version, usages.toString());
    }

    private static String foundProjectRepository(Map.Entry<RepositoryKey, Set<Usage>> e) {
        String repositoryName = e.getKey().name == null ? "<<no repository name found>>" : e.getKey().name;
        String repositoryId = e.getKey().id;
        String repositoryUrl = e.getKey().url;
        StringBuilder usages = new StringBuilder();
        for (Usage usage : e.getValue()) {
            usages
                    .append("  - project '")
                    .append(usage.projectId)
                    .append("' located at '")
                    .append(usage.relativePath)
                    .append("'")
                    .append(System.lineSeparator());
        }
        return """
                - Repository '%s' (with id '%s' and URL '%s') was declared in POM of following projects:
                %s
                """.formatted(repositoryName, repositoryId, repositoryUrl, usages.toString());
    }

    private static Path getSummaryPath() {
        return PluginUtils.TARGET_DIR.resolve(EXTRACTION_SUMMARY_FILE_NAME);
    }
}
