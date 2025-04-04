package io.quarkus.test.extractor.project.helper;

import io.quarkus.test.extractor.project.builder.Project;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;

import java.util.ArrayList;
import java.util.Map;

public abstract class TestProjectCustomizer {

    private static final Map<String, TestProjectCustomizer> CUSTOMIZERS;

    static {
        CUSTOMIZERS = Map.of("quarkus-websockets-next-deployment", createWebSocketsNextKotlinCustomizer());
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
