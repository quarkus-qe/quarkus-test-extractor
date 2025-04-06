package io.quarkus.test.extractor.project.helper;

import java.io.File;
import java.util.Set;

public record DisabledTest(String testClassPath, Set<String> artifactIds) {

    // disabled integration tests, if you need to disable an extension test, you need to implement it
    private static final Set<DisabledTest> DISABLED_TESTS = Set.of(
            // this test doesn't work even when I run it in Quarkus main project, no idea why
            new DisabledTest("io/quarkus/it/kubernetes/KindWithDefaultsTest.java",
                    Set.of("quarkus-integration-test-kubernetes-parent", "quarkus-integration-test-kubernetes-standard"))
    );

    public static boolean hasProjectDisabledTests(String artifactId) {
        return DISABLED_TESTS.stream().anyMatch(dt -> dt.artifactIds.contains(artifactId));
    }

    public static boolean isNotDisabledTest(String artifactId, File file) {
        String filePath = file.getPath();
        System.out.println("///////????/// file path is " + filePath);
        boolean neco = DISABLED_TESTS
                .stream()
                .filter(disabledTest -> disabledTest.artifactIds.contains(artifactId))
                .map(DisabledTest::testClassPath)
                .noneMatch(filePath::endsWith);
        if (!neco) {
            System.out.println("/////neconeco//// " + filePath);
        }
        return neco;
    }
}
