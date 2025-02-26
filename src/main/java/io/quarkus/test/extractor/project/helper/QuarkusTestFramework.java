package io.quarkus.test.extractor.project.helper;

import org.apache.maven.model.Dependency;

import java.util.Set;

public final class QuarkusTestFramework {

    /**
     * Some Quarkus Test Framework dependencies like 'quarkus-test-maven' may not be managed
     * by Quarkus BOM, may not be in the RHBQ, but we still expect them to be in the Maven repository.
     */
    private static final Set<String> TEST_FRAMEWORK_DEPENDENCIES = Set.of("quarkus-test-maven",
            "quarkus-devmode-test-utils");

    public static boolean isTestFrameworkDependency(Dependency dependency) {
        return TEST_FRAMEWORK_DEPENDENCIES.contains(dependency.getArtifactId());
    }

}
