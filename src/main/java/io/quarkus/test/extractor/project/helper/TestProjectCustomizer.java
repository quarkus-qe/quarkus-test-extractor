package io.quarkus.test.extractor.project.helper;

import io.quarkus.test.extractor.project.builder.Project;
import io.quarkus.test.extractor.project.utils.PluginUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Pattern;

public abstract class TestProjectCustomizer {

    private static final Map<String, TestProjectCustomizer> CUSTOMIZERS;

    static {
        CUSTOMIZERS = Map.of("quarkus-websockets-next-deployment", createWebSocketsNextKotlinCustomizer(),
                "quarkus-integration-test-devtools", createDevToolsItModuleCustomizer());
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
                String platformMetadataJson = PluginUtils
                        .getTargetProjectDirPath(project)
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
