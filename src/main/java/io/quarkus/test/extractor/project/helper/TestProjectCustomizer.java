package io.quarkus.test.extractor.project.helper;

import io.quarkus.test.extractor.project.builder.Project;
import io.quarkus.test.extractor.project.result.ParentProject;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import static io.quarkus.test.extractor.project.utils.MavenUtils.QUARKUS_PLATFORM_GROUP_ID;
import static io.quarkus.test.extractor.project.utils.MavenUtils.QUARKUS_CORE_BOM_VERSION;
import static io.quarkus.test.extractor.project.utils.MavenUtils.QUARKUS_PLATFORM_VERSION;
import static io.quarkus.test.extractor.project.utils.PluginUtils.getTargetProjectDirPath;

public abstract class TestProjectCustomizer {

    private static final Map<String, TestProjectCustomizer> CUSTOMIZERS;

    static {
        var customizers = new HashMap<>(Map.of("quarkus-websockets-next-deployment", createWebSocketsNextKotlinCustomizer(),
                "quarkus-integration-test-devtools", createDevToolsItModuleCustomizer(),
                "quarkus-integration-test-packaging", createPackagingItModuleCustomizer(),
                "quarkus-integration-test-main", createMainItModuleCustomizer(),
                "quarkus-integration-test-maven", createMavenItModuleCustomizer(),
                "quarkus-integration-test-hibernate-reactive-panache-kotlin", removeQuarkusRestKotlinExtension(),
                "quarkus-integration-test-class-transformer", addCreateExtRuntimePlugins(),
                "quarkus-integration-test-class-transformer-deployment", addCreateExtDeploymentPlugins(),
                // they are expected to not work in native
                "quarkus-integration-test-bouncycastle-jsse", disableInNative(),
                "quarkus-integration-test-bouncycastle-fips-jsse", disableInNative()
        ));

        customizers.put("quarkus-integration-test-rest-client-reactive-stork",
                useFixedVersionInMvnCompilerPluginAnnotationProcessorPath("io.smallrye.stork:stork-configuration-generator"));
        customizers.put("quarkus-integration-test-hibernate-orm-jpamodelgen",
                useFixedVersionInMvnCompilerPluginAnnotationProcessorPath("org.hibernate.orm:hibernate-jpamodelgen"));
        // we remove this property by default from all modules because it makes a mess depending on what Java
        // was used for the extraction, but VT IT parent actually needs this
        customizers.put("quarkus-virtual-threads-integration-tests-parent", setTargetJavaTo21InProfile());

        CUSTOMIZERS = Collections.unmodifiableMap(customizers);
    }

    // sets version to the first 'path' XML element of the compiler mvn plugin configuration,
    // because when not fixed, it fails with RHBQ
    private static TestProjectCustomizer useFixedVersionInMvnCompilerPluginAnnotationProcessorPath(String depManagementKey) {
        return new TestProjectCustomizer() {
            @Override
            protected void customize(Project project, Model model) {
                model.getBuild().getPlugins().stream()
                        .filter(p -> "maven-compiler-plugin".equalsIgnoreCase(p.getArtifactId()))
                        .findFirst()
                        .ifPresent(p -> {
                            var config = (org.codehaus.plexus.util.xml.Xpp3Dom) p.getConfiguration();
                            var path = config.getChild("annotationProcessorPaths").getChild("path");
                            var version = new Xpp3Dom("version");
                            version.setValue(QuarkusBom.getVersionForDependencyKey(depManagementKey));
                            path.addChild(version);
                        });
            }
        };
    }

    private static TestProjectCustomizer setTargetJavaTo21InProfile() {
        return new TestProjectCustomizer() {
            @Override
            protected void customize(Project project, Model model) {
                var profile = model.getProfiles().stream().filter(p -> "run-virtual-thread-tests".equalsIgnoreCase(p.getId())).findFirst().orElseThrow();
                var profileProperties = new Properties();
                profileProperties.put("maven.compiler.release", "21");
                if (profile.getProperties() != null) {
                    profileProperties.putAll(profile.getProperties());
                }
                profile.setProperties(profileProperties);
            }
        };
    }

    private static TestProjectCustomizer disableInNative() {
        return new TestProjectCustomizer() {
            @Override
            protected void customize(Project project, Model model) {
                model.getProperties().put("quarkus.build.skip", "$USE-EXTRACTED-PROPERTIES{quarkus.native.enabled}");
                var disableNativeProfile = getTargetProjectDirPath(project).resolve("disable-native-profile");
                if (!Files.exists(disableNativeProfile)) {
                    FileSystemStorage.saveFileContent(disableNativeProfile.toString(), "", true);
                }
            }
        };
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
                dependency.setGroupId("$USE-EXTRACTED-PROPERTIES{" + QUARKUS_PLATFORM_GROUP_ID + "}");
                dependency.setArtifactId("quarkus-bom-quarkus-platform-descriptor");
                dependency.setVersion("$USE-EXTRACTED-PROPERTIES{" + QUARKUS_PLATFORM_VERSION + "}");
                dependency.setClassifier("$USE-EXTRACTED-PROPERTIES{" + QUARKUS_PLATFORM_VERSION + "}");
                dependency.setType("json");
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
                        .replaceAll(Pattern.quote("{project.version}"), "{" + QUARKUS_CORE_BOM_VERSION + "}")
                        .replaceAll(Pattern.quote("{project.groupId}"), "{" + QUARKUS_PLATFORM_GROUP_ID + "}"));
            }
        };
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

    private static TestProjectCustomizer addCreateExtRuntimePlugins() {
        return new TestProjectCustomizer() {
            @Override
            protected void customize(Project project, Model model) {
                // see https://quarkus.io/guides/writing-extensions#using-maven for runtime module
                ParentProject.getProfile("create-extension-runtime-module").ifPresent(profile -> {
                    var extensionRuntimePlugins = profile.getBuild().getPlugins();
                    model.getBuild().getPlugins().addAll(extensionRuntimePlugins);
                });
            }
        };
    }

    private static TestProjectCustomizer addCreateExtDeploymentPlugins() {
        return new TestProjectCustomizer() {
            @Override
            protected void customize(Project project, Model model) {
                // see https://quarkus.io/guides/writing-extensions#using-maven for deployment module
                ParentProject.getProfile("create-extension-deployment-module").ifPresent(profile -> {
                    var extensionDeploymentPlugins = profile.getBuild().getPlugins();
                    model.getBuild().getPlugins().addAll(extensionDeploymentPlugins);
                });
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
