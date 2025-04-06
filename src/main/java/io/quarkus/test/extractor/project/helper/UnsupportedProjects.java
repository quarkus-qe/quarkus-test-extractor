package io.quarkus.test.extractor.project.helper;

import io.quarkus.test.extractor.project.builder.Project;

import java.util.Set;

public class UnsupportedProjects {

    /**
     * Relative paths to the projects that are currently not supported.
     */
    private static final Set<String> UNSUPPORTED_PROJECTS = Set.of(
            "integration-tests/kubernetes/maven-invoker-way",
            // following projects need more investigations before we start support them
            "integration-tests/test-extension", "integration-tests/grpc-external-proto-test"
    );

    public static boolean isNotSupportedProject(Project project) {
        String targetRelativePath = project.targetRelativePath();
        for (String unsupportedProject : UNSUPPORTED_PROJECTS) {
            if (targetRelativePath.contains(unsupportedProject)) {
                return true;
            }
        }
        return false;
    }

}
