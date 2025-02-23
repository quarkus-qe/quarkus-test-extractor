package io.quarkus.test.extractor.project.builder;

import org.apache.maven.project.MavenProject;

public sealed interface Project permits ProjectImpl {

    String artifactId();

    String relativePath();

    static Project extract(MavenProject project) {
        return new ProjectImpl(project);
    }

    boolean isDirectSubModule();

}
