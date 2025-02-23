package io.quarkus.test.extractor.project.builder;

import org.apache.maven.project.MavenProject;

import java.nio.file.Path;

record ProjectImpl(String artifactId, String relativePath) implements Project {

    private static final Path CURRENT_DIR = Path.of(".").toAbsolutePath();

    ProjectImpl(MavenProject mavenProject) {
        this(mavenProject.getArtifactId(), getRelativePath(mavenProject));
    }

    private static String getRelativePath(MavenProject mavenProject) {
        Path mavenProjectPath = mavenProject.getBasedir().toPath().toAbsolutePath();
        return CURRENT_DIR.relativize(mavenProjectPath).toString();
    }

    @Override
    public boolean isDirectSubModule() {
        return relativePath.split("/").length == 2;
    }

    // TODO: write operation must be synchronized!!!
    // {{test-module-name-id}}
    // {{test-module-artifact-id}}
    // {{quarkus-branch}}
    // {{quarkus-stream}}
    // {{must-be-replaced}}
    // TODO: generate into one big project with flat structure?
    // TODO: properties to keep up to date:
    // USE-EXTRACTED-PROPERTIES -> ''
    // $REPLACE{quarkus.platform.group-id}
    // $REPLACE{quarkus.platform.artifact-id}
    // $REPLACE{quarkus.platform.version}
    // $REPLACE{surefire-plugin.version}
    // $REPLACE{maven.home}
    // $REPLACE{project.build.directory}
    // $REPLACE{project.build.finalName}
    // $REPLACE{compiler-plugin.version}
}
