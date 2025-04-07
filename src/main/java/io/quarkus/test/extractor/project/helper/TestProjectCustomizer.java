package io.quarkus.test.extractor.project.helper;

import io.quarkus.test.extractor.project.builder.Project;
import io.quarkus.test.extractor.project.utils.MavenUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import static io.quarkus.test.extractor.project.utils.PluginUtils.getTargetProjectDirPath;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

public abstract class TestProjectCustomizer {

    private static final Map<String, TestProjectCustomizer> CUSTOMIZERS;

    static {
        CUSTOMIZERS = Map.of("quarkus-websockets-next-deployment", createWebSocketsNextKotlinCustomizer(),
                "quarkus-integration-test-devtools", createDevToolsItModuleCustomizer(),
                "quarkus-integration-test-rest-client-reactive-kotlin-serialization-with-validator",
                createKotlinSerializationWithValidatorCustomizer(),
                "quarkus-integration-test-packaging", createPackagingItModuleCustomizer(),
                "quarkus-integration-test-main", createMainItModuleCustomizer(),
                "quarkus-integration-test-maven", createMavenItModuleCustomizer(),
                "quarkus-integration-test-hibernate-reactive-panache-kotlin", removeQuarkusRestKotlinExtension());
    }

    private static TestProjectCustomizer createMavenItModuleCustomizer() {
        return new TestProjectCustomizer() {
            @Override
            protected void customize(Project project, Model model) {
                // solves following exception experienced when run io.quarkus.maven.it.TestMojoIT:
                // [ERROR] Resolving expression: '${maven.compiler.source}': Detected the following recursive
                // expression cycle in 'maven.compiler.source': [maven.compiler.source] @
                // [ERROR] Resolving expression: '${maven.compiler.target}': Detected the following
                // recursive expression cycle in 'maven.compiler.target': [maven.compiler.target] @
                var properties = model.getProperties();
                properties.put("maven.compiler.source", "$USE-EXTRACTED-PROPERTIES{maven.compiler.release}");
                properties.put("maven.compiler.target", "$USE-EXTRACTED-PROPERTIES{maven.compiler.release}");

                // I can't reproduce it locally but io.quarkus.maven.AddExtensionsMojoTest fails with:
                // Caused by: io.quarkus.registry.RegistryResolutionException:
                // Failed to resolve io.quarkus:quarkus-bom-quarkus-platform-descriptor:3.20.0:json:3.20.0
                model.setDependencies(new ArrayList<>(model.getDependencies()));
                var dependency = new Dependency();
                dependency.setGroupId("$USE-EXTRACTED-PROPERTIES{quarkus.platform.group-id}");
                dependency.setArtifactId("quarkus-bom-quarkus-platform-descriptor");
                dependency.setVersion("$USE-EXTRACTED-PROPERTIES{quarkus.platform.version}");
                dependency.setType(MavenUtils.POM);
                model.addDependency(dependency);
            }
        };
    }

    private TestProjectCustomizer() {

    }

    protected abstract void customize(Project project, Model model);

    public static void customizeIfNecessary(Project project, Model model) {
        final TestProjectCustomizer testProjectCustomizer = CUSTOMIZERS.get(project.artifactId());
        if (testProjectCustomizer != null) {
            testProjectCustomizer.customize(project, model);
        }
    }

    private static TestProjectCustomizer createMainItModuleCustomizer() {
        return new TestProjectCustomizer() {
            @Override
            protected void customize(Project project, Model model) {
                var fileChanger = new FileChanger(project, "src/main/resources/application.properties");
                fileChanger.changeContent(testContent -> testContent
                        .replace("io.quarkus\\:quarkus-integration-test-shared-library",
                            "io.quarkus.qe.tests\\:quarkus-integration-test-shared-library")
                        .replace("io.quarkus\\:quarkus-integration-test-main",
                            "io.quarkus.qe.tests\\:quarkus-integration-test-main"));
            }
        };
    }

    private static TestProjectCustomizer createPackagingItModuleCustomizer() {
        return new TestProjectCustomizer() {
            @Override
            protected void customize(Project project, Model model) {
                // we had to change test parent group id from 'io.quarkus' to 'io.quarkus.qe.tests',
                // mainly due to extensions modules, as if we want to test for example Quarkus Vert.x HTTP extension
                // tests, then we need 'io.quarkus:quarkus-vertx-http-deployment' inside extension modules because
                // some tests customize build steps etc., but tests for that extensions are also placed in the module
                // with the same artifact, so if we extracted tests into a Maven module with the same GAV
                // we would be testing our local sources and not RHBQ bits; on the other hand, we cannot just change
                // artifact id of a module where we place extracted tests because many tests has hardcoded expectations
                // related to the artifact id with which we execute these tests (e.g. Flyway migration file name)
                // so instead we have just a different group id for our extracted test modules, which is why we need to
                // change the 'quarkus.class-loading.removed-resources'."io.quarkus\:quarkus-integration-test-shared-library"
                // configuration property value to the one with the 'io.quarkus.qe.tests' group id instead
                var fileChanger = new FileChanger(project, "src/test/java/io/quarkus/removedclasses/AbstractRemovedResourceTest.java");
                fileChanger.changeContent(testContent -> {
                    testContent = testContent.replace("io.quarkus\\\\:quarkus-integration-test-shared-library",
                            "io.quarkus.qe.tests\\\\:quarkus-integration-test-shared-library");
                    return testContent;
                });
            }
        };
    }

    private static TestProjectCustomizer createKotlinSerializationWithValidatorCustomizer() {
        return new TestProjectCustomizer() {
            @Override
            protected void customize(Project project, Model model) {
                // io.quarkus.it.rest.client.BasicTest expects that during invalid input verification
                // that field 'validate.id' value is reported as too short string
                // the validation itself works, but the field is reported as 'validate.arg0'
                // I suspect there is some library missing or plugin missing or wrong path to classes,
                // but I failed to figure which one
                // TODO: if we start supporting Kotlin, this needs to be debugged
                var fileChanger = new FileChanger(project, "src/test/kotlin/io/quarkus/it/rest/client/BasicTest.kt");
                fileChanger.changeContent(testContent -> testContent.replace("validate.id", "validate.arg0"));
            }
        };
    }

    private static TestProjectCustomizer createWebSocketsNextKotlinCustomizer() {
        return new TestProjectCustomizer() {
            @Override
            protected void customize(Project project, Model model) {
                // otherwise cannot use 'addDependency' as somewhere I make it immutable list
                model.setDependencies(new ArrayList<>(model.getDependencies()));
                // io.quarkus.websockets.next.test.kotlin.KotlinWebSocketTest fails without Kotlin extension
                // because com.fasterxml.jackson.module.kotlin.KotlinModule is not registered with ObjectMapper
                // no idea why it is works in Quarkus project but this is what Quarkus Kotlin ref says anyway
                var quarkusKotlin = new Dependency();
                quarkusKotlin.setGroupId("io.quarkus");
                quarkusKotlin.setArtifactId("quarkus-kotlin");
                model.addDependency(quarkusKotlin);
                var jacksonModuleKotlin = new Dependency();
                jacksonModuleKotlin.setGroupId("com.fasterxml.jackson.module");
                jacksonModuleKotlin.setArtifactId("jackson-module-kotlin");
                model.addDependency(jacksonModuleKotlin);
            }
        };
    }

    private static TestProjectCustomizer createDevToolsItModuleCustomizer() {
        return new TestProjectCustomizer() {
            @Override
            protected void customize(Project project, Model model) {
                // required fix for io.quarkus.devtools.commands.CreateProjectPlatformMetadataTest
                var fileChanger = new FileChanger(project, "src/test/resources/platform-metadata.json");
                fileChanger.changeContent(fileContent -> fileContent
                        .replaceAll(Pattern.quote("{project.version}"), "{quarkus.platform.version}")
                        .replaceAll(Pattern.quote("{project.groupId}"), "{quarkus.platform.group-id}"));
            }
        };
    }

    private record FileChanger(Project project, String filePath) {

        private void changeContent(Function<String, String> replacement) {
            // this is the right way to get path for ITs, I didn't try it for extension modules
            var absolutePath = getTargetProjectDirPath(project).resolve(filePath);
            if (Files.exists(absolutePath)) {
                try {
                    String testContent = Files.readString(absolutePath);
                    Files.writeString(absolutePath, replacement.apply(testContent), TRUNCATE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to adjust file " + absolutePath, e);
                }
            } else {
                // this is not super important, but we should at least warn
                System.err.println("Failed to find file " + absolutePath + " which means implementation has changed");
            }
        }
    }

    /**
     * This works around of what is most likely flakiness (bug) in Quarkus, it removes Quarkus REST Kotlin extension
     * so that only transitive Quarkus Kotlin extension exists hopefully this will help with following error:
     * {@code
     * 09:37:08 [ERROR] Failed to execute goal io.quarkus:quarkus-maven-plugin:3.20.0:build (default)
     * on project quarkus-integration-test-hibernate-reactive-panache-kotlin: Failed to build quarkus application:
     * io.quarkus.builder.BuildException: Build failure: Build failed due to errors
     * 09:37:08 [ERROR]    [error]: Build step io.quarkus.deployment.steps.CapabilityAggregationStep#aggregateCapabilities
     * threw an exception: java.lang.IllegalStateException: Please make sure there is only one provider of the following
     * capabilities:
     * 09:37:08 [ERROR] capability io.quarkus.rest.kotlinx-serialization is provided by:
     * 09:37:08 [ERROR]   - io.quarkus:quarkus-rest-kotlin:3.20.0
     * 09:37:08 [ERROR]   - io.quarkus:quarkus-rest-kotlin:3.20.0
     * 09:37:08 [ERROR] capability io.quarkus.resteasy.reactive.json.kotlinx-serialization is provided by:
     * 09:37:08 [ERROR]   - io.quarkus:quarkus-rest-kotlin:3.20.0
     * 09:37:08 [ERROR]   - io.quarkus:quarkus-rest-kotlin:3.20.0
     * 09:37:08 [ERROR]
     * 09:37:08 [ERROR]    at io.quarkus.deployment.steps.CapabilityAggregationStep.aggregateCapabilities
     * (CapabilityAggregationStep.java:158)
     * 09:37:08 [ERROR]    at java.base/java.lang.invoke.MethodHandle.invokeWithArguments(MethodHandle.java:732)
     * 09:37:08 [ERROR]    at io.quarkus.deployment.ExtensionLoader$3.execute(ExtensionLoader.java:856)
     * 09:37:08 [ERROR]    at io.quarkus.builder.BuildContext.run(BuildContext.java:255)
     * 09:37:08 [ERROR]    at org.jboss.threads.ContextHandler$1.runWith(ContextHandler.java:18)
     * 09:37:08 [ERROR]    at org.jboss.threads.EnhancedQueueExecutor$Task.doRunWith(EnhancedQueueExecutor.java:2675)
     * 09:37:08 [ERROR]    at org.jboss.threads.EnhancedQueueExecutor$Task.run(EnhancedQueueExecutor.java:2654)
     * 09:37:08 [ERROR]    at org.jboss.threads.EnhancedQueueExecutor.runThreadBody(EnhancedQueueExecutor.java:1627)
     * 09:37:08 [ERROR]    at org.jboss.threads.EnhancedQueueExecutor$ThreadBody.run(EnhancedQueueExecutor.java:1594)
     * 09:37:08 [ERROR]    at java.base/java.lang.Thread.run(Thread.java:840)
     * 09:37:08 [ERROR]    at org.jboss.threads.JBossThread.run(JBossThread.java:499)
     * 09:37:08 [ERROR] -> [Help 1]
     * 09:37:08 [ERROR]
     * }
     * It is hard to provide a reproducer, so I did not open upstream issue yet.
     */
    private static TestProjectCustomizer removeQuarkusRestKotlinExtension() {
        return new TestProjectCustomizer() {
            @Override
            protected void customize(Project project, Model model) {
                removeDependency(model, "quarkus-rest-kotlin");
            }
        };
    }

    private static void removeDependency(Model model, String artifactId) {
        if (model.getDependencies() != null && !model.getDependencies().isEmpty()) {
            model.setDependencies(new ArrayList<>(model.getDependencies()));
            model.getDependencies().removeIf(dependency -> artifactId.equalsIgnoreCase(dependency.getArtifactId()));
        }
    }
}
