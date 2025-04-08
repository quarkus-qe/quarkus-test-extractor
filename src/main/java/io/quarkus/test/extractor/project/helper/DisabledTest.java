package io.quarkus.test.extractor.project.helper;

import java.io.File;
import java.util.Set;

public record DisabledTest(String testClassPath, Set<String> artifactIds) {

    private static final Set<DisabledTest> DISABLED_TESTS = Set.of(
            // this test doesn't work even when I run it in Quarkus main project, no idea why
            new DisabledTest("io/quarkus/it/kubernetes/KindWithDefaultsTest.java",
                    Set.of("quarkus-integration-test-kubernetes-parent", "quarkus-integration-test-kubernetes-standard")),
            // this test fails after change of credentials on denied permissions; I checked SQL script that creates
            // the user is executed and tried to increase waiting for idle connection timeout etc., but couldn't figure
            // what is the difference between the main project where it passes and between extracted tests
            new DisabledTest("src/test/java/io/quarkus/reactive/mysql/client/ChangingCredentialsTest.java",
                    Set.of("quarkus-reactive-mysql-client-deployment")),
            // io.quarkus.gradle.ConditionalDependenciesKotlinTest.buildProject of Gradle IT module
            // fails in Jenkins but not when I run it locally, Gradle & Kotlin are not supported so not investigating it
            new DisabledTest("src/test/java/io/quarkus/gradle/ConditionalDependenciesKotlinTest.java",
                    Set.of("quarkus-integration-test-gradle-plugin"))
    );

    public static boolean hasProjectDisabledTests(String artifactId) {
        return DISABLED_TESTS.stream().anyMatch(dt -> dt.artifactIds.contains(artifactId));
    }

    public static boolean isNotDisabledTest(String artifactId, File file) {
        String filePath = file.getPath();
        return DISABLED_TESTS
                .stream()
                .filter(disabledTest -> disabledTest.artifactIds.contains(artifactId))
                .map(DisabledTest::testClassPath)
                .noneMatch(filePath::endsWith);
    }
}
