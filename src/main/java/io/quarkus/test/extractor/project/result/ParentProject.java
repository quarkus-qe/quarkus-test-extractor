package io.quarkus.test.extractor.project.result;

import io.quarkus.test.extractor.project.builder.Project;
import io.quarkus.test.extractor.project.utils.MavenUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;

import java.nio.file.Path;
import java.util.*;

import static io.quarkus.test.extractor.project.utils.MavenUtils.*;

public final class ParentProject {

    // relative sub-paths for extensions subset of 'copy-as-is' artifact ids
    public static final String QUARKUS_ARC_TEST_SUPPLEMENT = "test-supplement";
    public static final String QUARKUS_SECURITY_TEST_UTILS = "security/test-utils";
    // super special cases that are not really a test modules, but we still need them
    private static final Set<String> COPY_AS_IS_ARTIFACT_IDS = Set.of("quarkus-integration-test-class-transformer-parent",
            "quarkus-integration-test-class-transformer-deployment", "quarkus-integration-test-class-transformer",
            "quarkus-integration-test-shared-library", "quarkus-integration-test-test-extension",
            "quarkus-integration-test-test-extension-extension",
            "quarkus-integration-test-test-extension-extension-deployment",
            "integration-test-extension-that-defines-junit-test-extensions-deployment",
            "integration-test-extension-that-defines-junit-test-extensions",
            "integration-test-extension-that-defines-junit-test-extensions-parent",
            "quarkus-arc-test-supplement", "quarkus-security-test-utils",
            "quarkus-integration-test-common-jpa-entities");
    private static final Model MAVEN_MODEL = MavenUtils.getMavenModel("pom-test-parent-skeleton.xml");
    private static final Map<String, String> PLUGIN_ARTIFACT_ID_TO_VERSION_PROP;

    static {
        // some of these versions may not be in the parent project, instead they are defined in the bootstrap
        // parent or else; it's no biggie, we can always fall back to the resolved plugin version which will most likely
        // be identical :-)
        var plugins = new HashMap<>(Map.of("kotlin-maven-plugin",
                "kotlin.version", "jandex-maven-plugin", "jandex.version",
                "smallrye-certificate-generator-maven-plugin", "smallrye-certificate-generator.version",
                "properties-maven-plugin", "properties-maven-plugin.version", "maven-clean-plugin",
                "version.clean.plugin", "nexus-staging-maven-plugin", "version.nexus-staging.plugin",
                "maven-resources-plugin", "version.resources.plugin", "scala-maven-plugin",
                "scala-maven-plugin.version", "quarkus-extension-maven-plugin", QUARKUS_CORE_BOM_VERSION,
                "maven-invoker-plugin", "maven-invoker-plugin.version"));
        plugins.put("build-helper-maven-plugin", "build-helper-plugin.version");
        plugins.put("quarkus-platform-bom-maven-plugin", "quarkus-platform-bom-plugin.version");
        PLUGIN_ARTIFACT_ID_TO_VERSION_PROP = Map.copyOf(plugins);
    }

    public static void correctGroupIdIfNecessary(Dependency dependency) {
        if (isManagedByTestParent(dependency)) {
            dependency.setGroupId(TEST_PARENT_GROUP_ID);
            dependency.setVersion(getThisProjectVersion());
        }
    }

    public static void addTestModule(String testModuleName, String profile) {
        findProfileByName(profile).addModule(testModuleName);
    }

    public static boolean isManagedByTestParent(Dependency dependency) {
        if (dependency == null) {
            return false;
        }
        return copyAsIsContainsArtifactId(dependency)
                || dependency.getArtifactId().startsWith("quarkus-integration-test")
                || MAVEN_MODEL.getDependencyManagement().getDependencies().stream()
                .anyMatch(d -> dependency.getArtifactId().equalsIgnoreCase(d.getArtifactId()));
    }

    private static boolean copyAsIsContainsArtifactId(Dependency dependency) {
        return COPY_AS_IS_ARTIFACT_IDS.contains(dependency.getArtifactId());
    }

    public static boolean copyAsIs(Project project) {
        return COPY_AS_IS_ARTIFACT_IDS.contains(project.artifactId())
                || (project.isIntegrationTestModule() && !hasJarPackaging(project));
    }

    public static void writeTo(Path targetDir) {
       writeParentMavenModel(MAVEN_MODEL, targetDir);
    }

    public static void setQuarkusVersion(String version) {
        MAVEN_MODEL.setVersion(version);
        MAVEN_MODEL.getProperties().put(QUARKUS_PLATFORM_VERSION, version);
        MAVEN_MODEL.getProperties().put(QUARKUS_COMMUNITY_VERSION, version);
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

    public static String getPluginVersionInParentProps(Plugin plugin) {
        String pluginArtifactId = plugin.getArtifactId();
        String propertyName = PLUGIN_ARTIFACT_ID_TO_VERSION_PROP.get(pluginArtifactId);
        if (propertyName != null && MAVEN_MODEL.getProperties().containsKey(propertyName)) {
            return "$USE-EXTRACTED-PROPERTIES{" + propertyName + "}";
        }
        // e.g. for 'docker-maven-plugin' try 'docker-maven-plugin.version'
        String pluginArtifactIdVersion = pluginArtifactId + ".version";
        if (MAVEN_MODEL.getProperties().containsKey(pluginArtifactIdVersion)) {
            return "$USE-EXTRACTED-PROPERTIES{" + pluginArtifactIdVersion + "}";
        }
        return null;
    }

    private static Profile findProfileByName(String profile) {
        return MAVEN_MODEL.getProfiles().stream().filter(p -> profile.equals(p.getId())).findFirst().orElseThrow();
    }

    public static void addManagedProject(Project project) {
        var managedDependency = new Dependency();
        managedDependency.setVersion("$USE-EXTRACTED-PROPERTIES{project.version}");
        managedDependency.setArtifactId(project.artifactId());
        managedDependency.setGroupId(TEST_PARENT_GROUP_ID);
        MAVEN_MODEL.getDependencyManagement().addDependency(managedDependency);
        if (project.isIntegrationTestModule()) {
            if (project.isDirectSubModule()) {
                getProfile("integration-tests-managed-modules" + getProfilePostfix(project))
                        .ifPresent(profile -> profile.addModule(project.targetRelativePath()));
            }
        } else {
            getProfile("extension-tests-managed-modules")
                    .ifPresent(profile -> profile.addModule(project.targetRelativePath()));
        }
    }

    public static Optional<Profile> getProfile(String x) {
        return MAVEN_MODEL
                .getProfiles()
                .stream()
                .filter(p -> x.equalsIgnoreCase(p.getId()))
                .findFirst();
    }

    public static void configureIntegrationTestsBuild(Project project) {
        var integrationTestsBuildProfile = getProfile("integration-tests-build").orElseThrow();
        integrationTestsBuildProfile.setBuild(project.originalModel().getBuild());
    }
}
