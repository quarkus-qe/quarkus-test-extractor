package io.quarkus.test.extractor.project.helper;

import io.quarkus.test.extractor.project.utils.MavenUtils;
import org.apache.maven.model.Model;

import java.util.Set;

import static io.quarkus.test.extractor.project.utils.PluginUtils.INTEGRATION_TESTS;
import static java.util.stream.Collectors.toUnmodifiableSet;

public class IntegrationTestModules {

    private static final String IT_MODULES_FILE_NAME = "integration-test-modules";
    private static volatile Set<String> itModules = null;

    /**
     * @return managed IT modules
     */
    private static Set<String> getItModules() {
        if (itModules == null) {
            String its = FileSystemStorage.loadFileContent(IT_MODULES_FILE_NAME);
            if (its.isEmpty()) {
                throw new IllegalStateException(
                        "No IT modules found, please run 'collect-project-metadata' goal first");
            }
            itModules = its.lines().map(String::trim).filter(s -> !s.isEmpty())
                    .map(s -> INTEGRATION_TESTS + "/" + s).collect(toUnmodifiableSet());
        }
        return itModules;
    }

    /**
     * @return true if given path represent a path of IT module managed by Quarkus IT parent POM file
     */
    public static boolean isDirectItModule(String relativePath) {
        return getItModules().stream().anyMatch(relativePath::equalsIgnoreCase);
    }

    public static boolean isItModuleParent(String artifactId) {
        return "quarkus-integration-tests-parent".equalsIgnoreCase(artifactId);
    }

    public static void addDirectItModules(Model itParentModel) {
        var profile = MavenUtils.getProfile(itParentModel, "test-modules")
                .orElseThrow(() -> new IllegalStateException("Profile 'test-modules' does not exist, "
                        + "which means managed IT modules cannot be collected"));
        if (profile.getModules().isEmpty()) {
            throw new IllegalStateException("Could not find any IT modules, Quarkus project has been reorganized");
        } else {
            String allManagedItModules = String.join(System.lineSeparator(), profile.getModules());
            FileSystemStorage.saveFileContent(IT_MODULES_FILE_NAME, allManagedItModules);
        }
    }

}
