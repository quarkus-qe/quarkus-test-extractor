package io.quarkus.test.extractor.project.builder;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.project.MavenProject;

import java.util.List;
import java.util.Properties;

public sealed interface Project permits ProjectImpl {

    static Project extract(MavenProject project) {
        return new ProjectImpl(project);
    }

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
    // TODO list here:
    //   - modules
    //   - plugin management??
    //   - plugin repositories
    //   - repositories!!
    //   - dependency management ??
    //   - profiles??
    //   - build!!!??!!
    //   - resources
    //   - dependencies
}
