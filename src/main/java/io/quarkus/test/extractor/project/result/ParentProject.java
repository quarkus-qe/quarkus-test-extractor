package io.quarkus.test.extractor.project.result;

import io.quarkus.test.extractor.project.utils.MavenUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;

import java.nio.file.Path;
import java.util.Properties;

public final class ParentProject {

    private static final Model MAVEN_MODEL = MavenUtils.getMavenModel("pom-test-parent-skeleton.xml");

    public static void addTestModule(String testModuleName, String profile) {
        findProfileByName(profile).addModule(testModuleName);
    }

    public static void writeTo(Path targetDir) {
       MavenUtils.writeMavenModel(MAVEN_MODEL, targetDir);
    }

    public static void setQuarkusVersion(String version) {
        MAVEN_MODEL.setVersion(version);
        MAVEN_MODEL.getProperties().put("quarkus.platform.version", version);
    }

    public static void addProperties(Properties properties) {
        if (MAVEN_MODEL.getProperties() == null) {
            MAVEN_MODEL.setProperties(new Properties());
        }
        if (properties != null) {
            properties.forEach((k, v) -> {
                if (MavenUtils.isNotIgnoredProperty((String) k)) {
                    MAVEN_MODEL.getProperties().put(k, v);
                }
            });
        }
    }

    public static boolean isPropertyDefinedInParentPom(String propertyName, String propertyValue) {
        if (MAVEN_MODEL.getProperties() == null) {
            return false;
        }
        String actualValue = MAVEN_MODEL.getProperties().getProperty(propertyName);
        if (actualValue == null) {
            return false;
        }
        return actualValue.equals(propertyValue);
    }

    private static Profile findProfileByName(String profile) {
        return MAVEN_MODEL.getProfiles().stream().filter(p -> profile.equals(p.getId())).findFirst().orElseThrow();
    }
}
