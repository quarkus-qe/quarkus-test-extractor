package io.quarkus.test.extractor.project.helper;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static io.quarkus.test.extractor.project.helper.FileSystemStorage.exists;
import static io.quarkus.test.extractor.project.helper.FileSystemStorage.loadFileContent;
import static io.quarkus.test.extractor.project.helper.FileSystemStorage.saveFileContent;
import static io.quarkus.test.extractor.project.utils.MavenUtils.getManagementKey;

// this is useful to determine what is in managed by modules with tests
// they are Quarkus Build Parent dependency management dependency keys
public record QuarkusBuildParent(Set<String> managementKeys) {

    private static final String QUARKUS_BUILD_PARENT_FILE_NAME = "quarkus-build-parent-context";
    private static final QuarkusBuildParent INSTANCE = load();

    public static void rememberDependencyManagement(DependencyManagement dependencyManagement) {
        if (dependencyManagement != null && dependencyManagement.getDependencies() != null) {
            dependencyManagement.getDependencies()
                    .forEach(dependency -> getManagementKeys().add(getManagementKey(dependency)));
            if (!getManagementKeys().isEmpty()) {
                saveFileContent(QUARKUS_BUILD_PARENT_FILE_NAME, serializeAsString());
            }
        }
    }

    public static boolean isNotManagedByBuildParent(Dependency dependency) {
        return getManagementKeys().contains(getManagementKey(dependency));
    }

    private static Set<String> getManagementKeys() {
        return INSTANCE.managementKeys;
    }

    private static QuarkusBuildParent load() {
        if (exists(QUARKUS_BUILD_PARENT_FILE_NAME)) {
            String content = loadFileContent(QUARKUS_BUILD_PARENT_FILE_NAME);
            Set<String> managementKeys = content
                    .lines()
                    .filter(l -> l != null && !l.isBlank())
                    .map(String::trim)
                    .collect(Collectors.toUnmodifiableSet());
            return new QuarkusBuildParent(managementKeys);
        } else {
            return new QuarkusBuildParent(new HashSet<>());
        }
    }

    private static String serializeAsString() {
        return getManagementKeys().stream()
                .map(String::trim)
                .collect(Collectors.joining(System.lineSeparator()));
    }
}
