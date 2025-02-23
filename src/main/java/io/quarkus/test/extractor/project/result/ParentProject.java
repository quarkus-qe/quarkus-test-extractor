package io.quarkus.test.extractor.project.result;

import io.quarkus.test.extractor.utils.MavenUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;

import java.io.File;
import java.nio.file.Path;

public final class ParentProject {

    private static final Model MAVEN_MODEL = MavenUtils.getMavenModel("pom-test-parent-skeleton.xml");

    public static void addTestModule(String testModuleName, String profile) {
        findProfileByName(profile).addModule(testModuleName);
    }

    public static void writeTo(Path targetDir) {
       MavenUtils.writeMavenModel(MAVEN_MODEL, getParentPomFile(targetDir));
    }

    private static Profile findProfileByName(String profile) {
        return MAVEN_MODEL.getProfiles().stream().filter(p -> profile.equals(p.getId())).findFirst().orElseThrow();
    }

    private static File getParentPomFile(Path targetDir) {
        return targetDir.resolve(MavenUtils.POM_XML).toFile();
    }
}
