package io.quarkus.test.extractor.project.helper;

import io.quarkus.test.extractor.project.builder.Project;
import io.quarkus.test.extractor.project.utils.PluginUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Pattern;

import static io.quarkus.test.extractor.project.utils.PluginUtils.getTargetProjectDirPath;

public abstract class TestProjectCustomizer {

    private static final Map<String, TestProjectCustomizer> CUSTOMIZERS;

    static {
        CUSTOMIZERS = Map.of("quarkus-websockets-next-deployment", createWebSocketsNextKotlinCustomizer(),
                "quarkus-integration-test-devtools", createDevToolsItModuleCustomizer(),
                "quarkus-integration-test-rest-client-reactive-kotlin-serialization-with-validator",
                createKotlinSerializationWithValidatorCustomizer());
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
                var basicTestPath = getTargetProjectDirPath(project).resolve("src/test/kotlin/io/quarkus/it/rest/client/BasicTest.kt");
                if (Files.exists(basicTestPath)) {
                    try {
                        String testContent = Files.readString(basicTestPath);
                        testContent =  testContent.replace("validate.id", "validate.arg0");
                        Files.writeString(basicTestPath, testContent);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to adjust file " + basicTestPath, e);
                    }
                } else {
                    // this is not super important, but we should at least warn
                    System.err.println("Failed to find file " + basicTestPath + " which means implementation has changed");
                }
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
                String platformMetadataJson = getTargetProjectDirPath(project)
                        .resolve("src/test/resources/platform-metadata.json")
                        .toAbsolutePath()
                        .toString();
                // otherwise this is resolved to 'io.quarkus.qe.tests' instead of 'io.quarkus'
                // but we cannot afford to use 'io.quarkus' for our module group ids
                String fileContent = FileSystemStorage.loadFileContent(platformMetadataJson)
                        .replaceAll(Pattern.quote("{project.version}"), "{quarkus.platform.version}")
                        .replaceAll(Pattern.quote("{project.groupId}"), "{quarkus.platform.group-id}");
                FileSystemStorage.replaceFileContent(platformMetadataJson, fileContent);
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
}
