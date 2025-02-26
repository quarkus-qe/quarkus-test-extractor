package io.quarkus.test.extractor.project.builder;

import io.quarkus.test.extractor.project.helper.ExtractionSummary;
import io.quarkus.test.extractor.project.helper.QuarkusBuildParent;
import io.quarkus.test.extractor.project.utils.PluginUtils;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import static io.quarkus.test.extractor.project.helper.ExtractionSummary.addNotManagedDependency;
import static io.quarkus.test.extractor.project.helper.ProductizedNotManagedDependencies.isProductizedButNotManaged;
import static io.quarkus.test.extractor.project.helper.QuarkusBom.isManagedByQuarkusBom;
import static io.quarkus.test.extractor.project.helper.QuarkusTestFramework.isTestFrameworkDependency;
import static io.quarkus.test.extractor.project.result.ParentProject.isManagedByTestParent;
import static io.quarkus.test.extractor.project.utils.MavenUtils.QUARKUS_COMMUNITY_VERSION;
import static io.quarkus.test.extractor.project.utils.MavenUtils.QUARKUS_PLATFORM_VERSION;
import static io.quarkus.test.extractor.project.utils.MavenUtils.getManagementKey;
import static io.quarkus.test.extractor.project.utils.MavenUtils.isNotCentralRepository;
import static io.quarkus.test.extractor.project.utils.MavenUtils.isTestJar;
import static io.quarkus.test.extractor.project.utils.MavenUtils.setQuarkusCommunityVersion;
import static io.quarkus.test.extractor.project.utils.MavenUtils.setQuarkusPlatformVersion;
import static io.quarkus.test.extractor.project.utils.PluginUtils.EXTENSIONS;
import static io.quarkus.test.extractor.project.utils.PluginUtils.INTEGRATION_TESTS;
import static io.quarkus.test.extractor.project.utils.PluginUtils.dropDeploymentPostfix;
import static io.quarkus.test.extractor.project.utils.MavenUtils.COMPILE_SCOPE;
import static io.quarkus.test.extractor.project.utils.MavenUtils.TEST_SCOPE;
import static io.quarkus.test.extractor.project.utils.MavenUtils.isTestModuleProperty;
import static io.quarkus.test.extractor.project.utils.PluginUtils.isDeploymentArtifact;
import static io.quarkus.test.extractor.project.utils.PluginUtils.prefixWithTests;

record ProjectImpl(MavenProject mavenProject, String relativePath, boolean isExtensionDeploymentModule)
        implements Project {

    private static final Path CURRENT_DIR = Path.of(".").toAbsolutePath();
    // ignoring 'maven-compiler-plugin' could be an issue as sometimes there is a special configuration / execution
    // let's reevaluate it case by case when we experience issue so that we understand the differences
    private static final Set<String> IGNORED_PLUGINS = Set.of("maven-compiler-plugin", "forbiddenapis",
            "maven-jar-plugin", "templating-maven-plugin", "quarkus-maven-plugin", "maven-enforcer-plugin",
            "impsort-maven-plugin");

    private ProjectImpl(MavenProject mavenProject, String relativePath) {
        this(mavenProject, relativePath, PluginUtils.isExtensionDeploymentModule(relativePath));
    }

    ProjectImpl(MavenProject mavenProject) {
        this(mavenProject, extractRelativePath(mavenProject));
    }

    @Override
    public List<Profile> profiles() {
        List<Profile> profiles = new ArrayList<>();
        mavenProject.getOriginalModel().getProfiles().forEach(profile -> profiles.add(profile.clone()));
        return List.copyOf(profiles);
    }

    @Override
    public Build build() {
        if (mavenProject.getOriginalModel().getBuild() == null) {
            return null;
        }
        Build build = mavenProject.getOriginalModel().getBuild().clone();
        if (build.getPlugins() != null) {
            build.getPlugins().removeIf(plugin -> IGNORED_PLUGINS.contains(plugin.getArtifactId()));
        }
        return build;
    }

    @Override
    public DependencyManagement dependencyManagement() {
        if (!containsTests()) {
            // don't care, we are not going to use it anyway
            return mavenProject.getDependencyManagement();
        }
        DependencyManagement dependencyManagement = mavenProject.getOriginalModel().getDependencyManagement();
        if (dependencyManagement == null || dependencyManagement.getDependencies() == null
                || dependencyManagement.getDependencies().isEmpty()) {
            return null;
        }
        List<Dependency> managedDependencies = dependencyManagement.getDependencies().stream()
                .filter(QuarkusBuildParent::isNotManagedByBuildParent).toList();
        if (!managedDependencies.isEmpty()) {
            // really?? that is suspicious, let's add it to summary so that someone can inspect this fact manually
            ExtractionSummary.addProjectWithDependencyManagement(dependencyManagement, this);
            return dependencyManagement;
        }
        return null;
    }

    @Override
    public List<Dependency> dependencies() {
        if (mavenProject.getDependencies() == null || mavenProject.getDependencies().isEmpty()) {
            return List.of();
        }
        List<Dependency> result = new ArrayList<>();
        if (isExtensionDeploymentModule) {
            var self = new Dependency();
            self.setGroupId(mavenProject.getGroupId());
            self.setArtifactId(mavenProject.getArtifactId());
            self.setScope(TEST_SCOPE);
            if (!isManagedByQuarkusBom(self)) {
                // ATM at the very least 'quarkus-observability-devservices-deployment' is not managed in '-deployment'
                // module, and it doesn't seem to be an issue, so not a bug
                setQuarkusPlatformVersion(self);
            }
            result.add(self);
            mavenProject.getOriginalModel().getDependencies().forEach(dep -> {
                var dependency = dep.clone();
                if (!TEST_SCOPE.equalsIgnoreCase(dependency.getScope())) {
                    dependency.setScope(TEST_SCOPE);
                }
                // some test scope dependencies probably are not managed by Quarkus BOM
                // but are managed due to Quarkus Build Parent dependency management
                // however we only use delivered artifacts and use Quarkus platform BOM
                // for testing so we need to set the version explicitly
                if (dependency.getVersion() == null || dependency.getVersion().isEmpty()) {
                    if (isDeploymentArtifact(dependency) && isTestJar(dependency)) {
                        // deployment module signals it is Quarkus core extension module
                        // and when it is a test jar, it is not managed (so far I didn't see Quarkus BOM to manage it)
                        // so let's just use Quarkus Platform version because that should fit
                        // also, test-jars are not productized
                        setQuarkusPlatformVersion(dependency);
                        addNotManagedDependency(dependency, this, QUARKUS_PLATFORM_VERSION);
                    } else if (!isManagedByQuarkusBom(dependency) && !isManagedByTestParent(dependency)) {
                        resolveAndSetDependencyVersion(dependency);
                    }
                }
                result.add(dependency);
            });
        } else {
            mavenProject.getOriginalModel().getDependencies().forEach(dep -> {
                var dependency = dep.clone();
                if (COMPILE_SCOPE.equalsIgnoreCase(dependency.getScope())) {
                    // use default scope, usually developers doesn't type it either
                    dependency.setScope(null);
                }
                // TODO: we might be able to drop deployment modules
                //   I think they are there mostly to ensure order during builds
                //   but there are exceptions, like if there is internal JUnit fw
                //   that customizes build steps or when there is an extension submodule
                // when it is a deployment artifact, I assume it is core extension
                // so managed and no version needed
                if (!isDeploymentArtifact(dependency) && (dependency.getVersion() == null
                        || dependency.getVersion().isEmpty())) {
                    if (!isManagedByQuarkusBom(dependency) && !isManagedByTestParent(dependency)
                            && notAccompaniedWithDeploymentDep(dependency)) {
                        resolveAndSetDependencyVersion(dependency);
                    }
                }
                result.add(dependency);
            });
        }
        return List.copyOf(result);
    }

    @Override
    public List<Repository> repositories() {
        if (mavenProject.getRepositories() == null || mavenProject.getRepositories().isEmpty()) {
            return List.of();
        }
        List<Repository> result = new ArrayList<>();
        mavenProject.getRepositories().forEach(repo -> {
            if (isNotCentralRepository(repo)) {
                var repository = repo.clone();
                ExtractionSummary.addRepository(repository, this);
                result.add(repository);
            }
        });
        return List.copyOf(result);
    }

    @Override
    public List<Repository> pluginRepositories() {
        if (mavenProject.getPluginRepositories() == null || mavenProject.getPluginRepositories().isEmpty()) {
            return List.of();
        }
        List<Repository> result = new ArrayList<>();
        mavenProject.getPluginRepositories().forEach(repo -> {
            if (isNotCentralRepository(repo)) {
                var repository = repo.clone();
                ExtractionSummary.addPluginRepository(repository, this);
                result.add(repository);
            }
        });
        return List.copyOf(result);
    }

    private boolean notAccompaniedWithDeploymentDep(Dependency dependency) {
        // ideally this should be useless (and maybe it is?) but in case artifact
        // doesn't have version set, and we don't find it as managed by Quarkus BOM
        // this serves as additional check that it is REALLY not managed
        // because setting dependency version explicitly is a big deal, we want to test the delivered bits
        // AKA: if you have vertx-http and vertx-http-deployment -> vertx-http must be managed
        String artifactId = dependency.getArtifactId();
        return mavenProject.getOriginalModel()
                .getDependencies()
                .stream()
                .filter(d -> d != dependency)
                .filter(d -> d.getArtifactId().startsWith(artifactId))
                .filter(PluginUtils::isDeploymentArtifact)
                .map(Dependency::getArtifactId)
                .map(PluginUtils::dropDeploymentPostfix)
                .noneMatch(artifactId::equalsIgnoreCase);
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
            return prefixWithTests(dropDeploymentPostfix(mavenProject.getArtifactId()));
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

    @Override
    public Model originalModel() {
        return mavenProject.getOriginalModel().clone();
    }

    private void resolveAndSetDependencyVersion(Dependency dependency) {
        String actualDependencyVersion = findDependencyVersion(dependency);
        if (isTestFrameworkDependency(dependency)) {
            // some test framework dependencies are not managed by Quarkus BOM
            setQuarkusCommunityVersion(dependency);
            addNotManagedDependency(dependency, this, QUARKUS_COMMUNITY_VERSION);
        } else if (isProductizedButNotManaged(dependency)) {
            setQuarkusPlatformVersion(dependency);
            addNotManagedDependency(dependency, this, QUARKUS_PLATFORM_VERSION);
        } else if (actualDependencyVersion == null) {
            setQuarkusPlatformVersion(dependency);
            addNotManagedDependency(dependency, this, QUARKUS_PLATFORM_VERSION);
        } else if (actualDependencyVersion.equalsIgnoreCase(version())) {
            setQuarkusCommunityVersion(dependency);
            addNotManagedDependency(dependency, this, QUARKUS_COMMUNITY_VERSION);
        } else {
            dependency.setVersion(actualDependencyVersion);
            addNotManagedDependency(dependency, this);
        }
    }

    private String findDependencyVersion(Dependency dependency) {
        // find version among resolved dependencies
        String managementKey = getManagementKey(dependency);
        return mavenProject.getDependencies().stream()
                .filter(d -> d.getVersion() != null)
                .filter(d -> managementKey.equalsIgnoreCase(getManagementKey(d)))
                .map(Dependency::getVersion)
                .findFirst()
                .orElse(null);
    }

    private static String extractRelativePath(MavenProject mavenProject) {
        Path mavenProjectPath = mavenProject.getBasedir().toPath().toAbsolutePath();
        return CURRENT_DIR.relativize(mavenProjectPath).toString();
    }

}
