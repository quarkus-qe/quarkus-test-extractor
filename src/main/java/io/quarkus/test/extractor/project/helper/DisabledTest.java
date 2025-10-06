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
                    Set.of("quarkus-integration-test-gradle-plugin")),
            // this test is also failing in native in Quarkus main project, they probably don't run it in native
            new DisabledTest("src/test/java/io/quarkus/it/hibernate/multitenancy/fruit/HibernateTenancyFunctionalityInGraalITCase.java",
                    Set.of("quarkus-integration-test-hibernate-orm-tenancy-discriminator", "quarkus-integration-test-hibernate-orm-tenancy")),
            // this test cannot be run in native at all because it uses @Inject with @QuarkusIntegrationTest...
            new DisabledTest("src/test/java/io/quarkus/it/opentelemetry/minimal/HelloServiceIT.java",
                    Set.of("quarkus-integration-test-opentelemetry-minimal")),
            // I don't think they run this test in native in the Quarkus Main project, but it doesn't start for me
            // locally so it's hard to debug at all
            new DisabledTest("src/test/java/io/quarkus/it/keycloak/SmallRyeJwtOidcWebAppInGraalITCase.java",
                    Set.of("quarkus-integration-test-smallrye-jwt-oidc-webapp")),
            // once more - this test doesn't start when I run it locally so it's hard to debug, but I can see it
            // running as part of upstream native CI, so if DB2 reactive client is any concern, we should investigate
            new DisabledTest("src/test/java/io/quarkus/it/reactive/db2/client/NativeQueryIT.java",
                    Set.of("quarkus-integration-test-reactive-db2-client")),
            // following 2 Maven invoker native tests don't use (propagate) native image builder and fail over
            // UBI8/UBI9 conflict (that "/lib64/libc.so.6: version `GLIBC_2.33' not found"), don't know why it doesn't
            // fail upstream if they run it in native (which I tried to find but didn't), but it is sort of a problem
            // that can solve itself when they migrate everything to UBI9
            new DisabledTest("src/test/java/io/quarkus/maven/it/NativeImageIT.java",
                    Set.of("quarkus-integration-test-maven")),
            new DisabledTest("src/test/java/io/quarkus/maven/it/NativeAgentIT.java",
                    Set.of("quarkus-integration-test-maven")),
            // this test uses @Inject with @QuarkusIntegrationTest so it could never work
            new DisabledTest("src/test/java/org/acme/ClientCallingResourceIT.java",
                    Set.of("quarkus-integration-test-smallrye-stork-registration")),
            // following 2 OTel ITs cannot pass in native as they change build-time property at runtime,
            // the quarkus.otel.enabled configuration property, I think upstream doesn't run them in native,
            // or I don't have explanation...
            new DisabledTest("src/test/java/io/quarkus/it/opentelemetry/OpenTelemetryDisabledIT.java",
                    Set.of("quarkus-integration-test-opentelemetry-quickstart")),
            new DisabledTest("src/test/java/io/quarkus/it/opentelemetry/OpenTelemetryIT.java",
                    Set.of("quarkus-integration-test-opentelemetry-quickstart")),
            // next 2 Gradle tests only fail with RHBQ over some resolution, disabling as don't have time to investigate
            // Gradle that is not supported
            new DisabledTest("src/test/java/io/quarkus/gradle/CompileOnlyDependencyFlagsTest.java",
                    Set.of("quarkus-integration-test-gradle-plugin")),
            new DisabledTest("src/test/java/io/quarkus/gradle/QuarkusPluginFunctionalTest.java",
                    Set.of("quarkus-integration-test-gradle-plugin")),
            // next 2 tests works with community Quarkus, fails with RHBQ when it tries to resolve
            // io.quarkiverse.qute.web:quarkus-qute-web:jar:codestarts:3.3.0 locally (the test is configured to go offline)
            // but this dependency is nowhere to be find with RHBQ and I can't see where is it referred from anywhere
            new DisabledTest("src/test/java/io/quarkus/maven/AddExtensionMojoTest.java",
                    Set.of("quarkus-integration-test-maven")),
            new DisabledTest("src/test/java/io/quarkus/maven/AddExtensionsMojoTest.java",
                    Set.of("quarkus-integration-test-maven")),
            // there is wrong 'user.home' inside actual application, it is probably related with the fact that creating
            // the application model fails over resolving 'quarkus-devui-deployment'
            // it could probably be fixed
            new DisabledTest("src/test/java/io/quarkus/devui/devmcp/DevMcpTest.java",
                    Set.of("quarkus-devui-deployment")),
            // this looks like a class loader issue (which is probably a core cause of many disabled test failures above)
            // when io.smallrye.jwt.util.ResourceUtils#getAsClasspathResource is loading a file in a build step in a DEV
            // mode test, it doesn't see the test class path, but actual application path
            new DisabledTest("src/test/java/io/quarkus/jwt/test/dev/SmallryeJwtPersistentColdStartupSignedTest.java",
                    Set.of("quarkus-smallrye-jwt-deployment")),
            new DisabledTest("src/test/java/io/quarkus/jwt/test/dev/SmallryeJwtPersistentColdStartupEncryptedTest.java",
                    Set.of("quarkus-smallrye-jwt-deployment")),
            // disabling due to NPE in a programmatic CDI lookup, TODO: investigate
            new DisabledTest("src/test/java/io/quarkus/opentelemetry/deployment/logs/OtelLogsHandlerDisabledTest.java",
                    Set.of("quarkus-opentelemetry-deployment")),
            // this also looks like issue with a classloader
            new DisabledTest("src/test/java/io/quarkus/it/hibernate/multitenancy/fruit/HibernateTenancyFunctionalityInGraalITCase.java",
                    Set.of("quarkus-integration-test-hibernate-orm-tenancy-schema-mariadb"))
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
