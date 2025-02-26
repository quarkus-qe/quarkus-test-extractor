package io.quarkus.test.extractor.project.helper;

import org.apache.maven.model.Dependency;

import java.util.Set;

public final class ProductizedNotManagedDependencies {

    // these dependencies are not managed by Quarkus BOM, because they are not used directly by users,
    // but they are still expected to be productized
    private static final Set<String> DEPENDENCIES = Set.of("quarkus-reactive-datasource-deployment");

    public static boolean isProductizedButNotManaged(Dependency dependency) {
        return DEPENDENCIES.contains(dependency.getArtifactId());
    }

}
