package io.quarkus.test.extractor.project.result;

import io.quarkus.test.extractor.project.builder.Project;
import io.quarkus.test.extractor.utils.MavenUtils;
import org.apache.maven.model.Model;

public final class TestModuleProject {

    private static final Model MAVEN_MODEL = MavenUtils.getMavenModel("pom-test-module-skeleton.xml");

    public static Model create(Project project) {
        Model model = MAVEN_MODEL.clone();
        model.setVersion(project.version());
        model.getParent().setVersion(project.version());
        model.setArtifactId(project.artifactId());
        model.setName(project.name());
        model.setProperties(project.properties());
        return model;
    }
}
