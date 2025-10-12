package io.quarkus.test.extractor.project.helper;

import io.quarkus.test.extractor.project.builder.Project;

import java.io.File;
import java.util.Set;

public class UnsupportedProjects {

    /**
     * Relative paths to the projects that are currently not supported.
     */
    private static final Set<String> UNSUPPORTED_PROJECTS = Set.of(
            "integration-tests/kubernetes/maven-invoker-way",
            // following projects need more investigations before we start support them
            "integration-tests/test-extension", "integration-tests/grpc-external-proto-test",
            // fails and gelf is not supported, so I did not investigate
            "integration-tests/logging-gelf",
            // these tests pass with Quarkus community but fail with RHBQ, customizations would be extensive,
            // we need to fix the tests in Quarkus main project instead of doing it here, and then we can enable
            // the module for streams in which we will fix it
            "integration-tests/devtools/",
            // seems like Oracle is not starting and I don't have time to investigate
            // TODO: this should be fixable
            "integration-tests/jpa-oracle"
    );

    public static boolean isNotSupportedProject(Project project) {
        String targetRelativePath = project.targetRelativePath();
        for (String unsupportedProject : UNSUPPORTED_PROJECTS) {
            if (targetRelativePath.contains(unsupportedProject)
                    || (targetRelativePath + File.separator).contains(unsupportedProject)) {
                return true;
            }
        }
        return false;
    }

}
