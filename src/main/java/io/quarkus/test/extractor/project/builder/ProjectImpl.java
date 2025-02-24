package io.quarkus.test.extractor.project.builder;

import io.quarkus.test.extractor.utils.ConstantUtils;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Pattern;

import static io.quarkus.test.extractor.utils.ConstantUtils.EXTENSIONS;
import static io.quarkus.test.extractor.utils.ConstantUtils.INTEGRATION_TESTS;
import static io.quarkus.test.extractor.utils.ConstantUtils.dropDeploymentPostfix;
import static io.quarkus.test.extractor.utils.MavenUtils.isTestModuleProperty;

record ProjectImpl(MavenProject mavenProject, String relativePath, boolean isExtensionDeploymentModule)
        implements Project {

    private static final Path CURRENT_DIR = Path.of(".").toAbsolutePath();

    private ProjectImpl(MavenProject mavenProject, String relativePath) {
        this(mavenProject, relativePath, ConstantUtils.isExtensionDeploymentModule(relativePath));
    }

    ProjectImpl(MavenProject mavenProject) {
        this(mavenProject, extractRelativePath(mavenProject));
    }

    @Override
    public String name() {
        if (isExtensionDeploymentModule) {
            return dropDeploymentPostfix(mavenProject.getName());
        }
        return mavenProject.getName();
    }

    @Override
    public String version() {
        return mavenProject.getVersion();
    }

    @Override
    public String artifactId() {
        if (isExtensionDeploymentModule) {
            return dropDeploymentPostfix(mavenProject.getArtifactId());
        }
        return mavenProject.getArtifactId();
    }

    @Override
    public String targetRelativePath() {
        return isExtensionDeploymentModule ?
                // extensions/vertx-http/deployment -> extensions/vertx-http
                extractRelativePath(mavenProject.getParent())
                : relativePath;
    }

    @Override
    public boolean isDirectSubModule() {
        if (isExtensionDeploymentModule) {
            return true;
        }
        // integration tests - then we want to differ between integration test module
        // and its submodules
        return relativePath.split(Pattern.quote(File.separator)).length == 2;
    }

    @Override
    public boolean containsTests() {
        // ATM tests which are testing Quarkus application (not individual classes)
        // are present in extension deployment modules and integration test modules
        if (isExtensionDeploymentModule) {
            return true;
        }
        return relativePath.startsWith(INTEGRATION_TESTS + File.separator);
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

}
