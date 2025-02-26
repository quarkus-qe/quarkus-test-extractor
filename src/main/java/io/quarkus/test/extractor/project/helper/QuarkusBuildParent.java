package io.quarkus.test.extractor.project.helper;

import io.quarkus.test.extractor.project.builder.Project;
import io.quarkus.test.extractor.project.utils.MavenUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static io.quarkus.test.extractor.project.utils.MavenUtils.getManagementKey;

public class QuarkusBuildParent {

    // this is useful to determine what is in managed by modules with tests
    // they are Quarkus Build Parent dependency management dependency keys
    private static final Set<String> MANAGEMENT_KEYS = ConcurrentHashMap.newKeySet();

    public static void rememberDependencyManagement(DependencyManagement dependencyManagement) {
        if (dependencyManagement != null && dependencyManagement.getDependencies() != null) {
            dependencyManagement.getDependencies()
                    .forEach(dependency -> MANAGEMENT_KEYS.add(getManagementKey(dependency)));
        }
    }

    public static boolean isNotManagedByBuildParent(Dependency dependency) {
        return MANAGEMENT_KEYS.contains(getManagementKey(dependency));
    }
}
