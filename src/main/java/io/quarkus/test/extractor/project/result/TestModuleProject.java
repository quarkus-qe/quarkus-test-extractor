package io.quarkus.test.extractor.project.result;

import io.quarkus.test.extractor.project.builder.Project;
import io.quarkus.test.extractor.project.utils.MavenUtils;
import org.apache.maven.model.Model;

import static io.quarkus.test.extractor.project.utils.MavenUtils.TEST_PARENT_GROUP_ID;
import static io.quarkus.test.extractor.project.utils.MavenUtils.computeRelativePath;

public final class TestModuleProject {

    private static final Model MAVEN_MODEL = MavenUtils.getMavenModel("pom-test-module-skeleton.xml");

    public static Model create(Project project) {
        Model model = MAVEN_MODEL.clone();
        model.setVersion(project.version());
        if (project.isDirectSubModule()) {
            model.getParent().setVersion(project.version());
            model.getParent().setRelativePath(computeRelativePath(project));
        } else {
            model.setParent(project.originalModel().getParent());
        }
        model.getParent().setGroupId(TEST_PARENT_GROUP_ID);
        model.setArtifactId(project.artifactId());
        model.setName(project.name());
        model.setProperties(project.properties());
        model.setDependencies(project.dependencies());
        model.setRepositories(project.repositories());
        model.setPluginRepositories(project.pluginRepositories());
        var dependencyManagement = project.dependencyManagement();
        if (dependencyManagement != null) {
            model.setDependencyManagement(dependencyManagement);
        }
        model.setBuild(project.build());
        model.setProfiles(project.profiles());
        model.setDescription("Tests extracted from project " + project.originalProjectName());
        return model;
    }
}
