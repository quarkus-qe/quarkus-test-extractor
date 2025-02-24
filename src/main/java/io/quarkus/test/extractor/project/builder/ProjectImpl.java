package io.quarkus.test.extractor.project.builder;

import io.quarkus.test.extractor.utils.MavenUtils;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Path;
import java.util.Properties;

import static io.quarkus.test.extractor.utils.ConstantUtils.EXTENSIONS;
import static io.quarkus.test.extractor.utils.ConstantUtils.INTEGRATION_TESTS;
import static io.quarkus.test.extractor.utils.ConstantUtils.isExtensionDeploymentModule;
import static io.quarkus.test.extractor.utils.MavenUtils.isTestModuleProperty;

record ProjectImpl(MavenProject mavenProject, String relativePath) implements Project {

    private static final Path CURRENT_DIR = Path.of(".").toAbsolutePath();

    ProjectImpl(MavenProject mavenProject) {
        this(mavenProject, extractRelativePath(mavenProject));
    }

    @Override
    public String version() {
        return mavenProject.getVersion();
    }

    @Override
    public String artifactId() {
        return mavenProject.getArtifactId();
    }

    @Override
    public String targetRelativePath() {
        return isExtensionDeploymentModule(relativePath()) ?
                // extensions/vertx-http/deployment -> extensions/vertx-http
                extractRelativePath(mavenProject.getParent())
                : relativePath;
    }

    @Override
    public boolean isDirectSubModule() {
        if (isExtensionDeploymentModule(relativePath())) {
            return true;
        }
        // integration tests - then we want to differ between integration test module
        // and its submodules
        return relativePath().split("\\\\" + File.separator).length == 2;
    }

    @Override
    public boolean containsTests() {
        // ATM tests which are testing Quarkus application (not individual classes)
        // are present in extension deployment modules and integration test modules
        String relativePath = relativePath();
        if (relativePath.startsWith(INTEGRATION_TESTS + File.separator)) {
            return true;
        }
        return isExtensionDeploymentModule(relativePath);
    }

    @Override
    public Properties properties() {
        if (mavenProject.getProperties() != null && containsTests()) {
            Properties testModuleProperties = new Properties();
            mavenProject.getProperties().forEach((k, v) -> {
                String propertyName = (String) k;
                String propertyValue = (String) v;
                if (isTestModuleProperty(propertyName, propertyValue)) {
                    testModuleProperties.put(propertyName, propertyValue);
                }
            });

            return testModuleProperties;
        }

        // effective properties, we don't need to repeat them everywhere
        // so this is mainly for copying them once to the project parent
        final Properties properties = new Properties();
        if (mavenProject.getProperties() != null) {
            properties.putAll(mavenProject.getProperties());
        }
        return properties;
    }

    @Override
    public String targetProfileName() {
        return relativePath().startsWith(EXTENSIONS) ? EXTENSIONS : INTEGRATION_TESTS;
    }

    private static String extractRelativePath(MavenProject mavenProject) {
        Path mavenProjectPath = mavenProject.getBasedir().toPath().toAbsolutePath();
        return CURRENT_DIR.relativize(mavenProjectPath).toString();
    }

    // TODO: write operation must be synchronized!!!
    // {{test-module-name-id}}
    // {{test-module-artifact-id}}
    // {{quarkus-branch}}
    // {{quarkus-stream}}
    // {{must-be-replaced}}
    // TODO: generate into one big project with flat structure?
    // TODO: properties to keep up to date:
    // USE-EXTRACTED-PROPERTIES -> ''
    // $REPLACE{quarkus.platform.group-id}
    // $REPLACE{quarkus.platform.artifact-id}
    // $REPLACE{quarkus.platform.version}
    // $REPLACE{surefire-plugin.version}
    // $REPLACE{maven.home}
    // $REPLACE{project.build.directory}
    // $REPLACE{project.build.finalName}
    // $REPLACE{compiler-plugin.version}
}
