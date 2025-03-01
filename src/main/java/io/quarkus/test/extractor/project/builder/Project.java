package io.quarkus.test.extractor.project.builder;

import io.quarkus.test.extractor.project.helper.ExtractionSummary;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

public sealed interface Project permits ProjectImpl {

    static Project extract(MavenProject project, ExtractionSummary extractionSummary) {
        return new ProjectImpl(project, extractionSummary);
    }

    List<Profile> profiles();

    Build build();

    DependencyManagement dependencyManagement();

    List<Dependency> dependencies();

    List<Repository> repositories();

    List<Repository> pluginRepositories();

    String name();

    String version();

    String artifactId();

    String relativePath();

    String targetRelativePath();

    boolean isDirectSubModule();

    boolean containsTests();

    /**
     * @return Maven properties from the 'properties' xml element
     */
    Properties properties();

    /**
     * @return Profile where this project should be placed in generated (target) project.
     */
    String targetProfileName();

    /**
     * @return Original model in case this project needs to be copies 'as is'.
     */
    Model originalModel();

    String packagingType();

    boolean isIntegrationTestModule();

    Path projectPath();
}
