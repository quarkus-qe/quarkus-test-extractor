package io.quarkus.test.extractor.project.builder;

import org.apache.maven.project.MavenProject;

import java.util.Properties;

public sealed interface Project permits ProjectImpl {

    static Project extract(MavenProject project) {
        return new ProjectImpl(project);
    }

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
