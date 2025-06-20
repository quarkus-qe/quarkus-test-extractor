package io.quarkus.test.extractor.project.builder;

import io.quarkus.test.extractor.project.helper.*;
import io.quarkus.test.extractor.project.result.ParentProject;
import io.quarkus.test.extractor.project.utils.MavenUtils;
import io.quarkus.test.extractor.project.utils.PluginUtils;
import org.apache.maven.model.Build;
import org.apache.maven.model.BuildBase;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static io.quarkus.test.extractor.project.helper.KnownTestJars.setTestJarVersionIfNecessary;
import static io.quarkus.test.extractor.project.helper.ProductizedNotManagedDependencies.isProductizedButNotManaged;
import static io.quarkus.test.extractor.project.helper.QuarkusBom.isManagedByQuarkusBom;
import static io.quarkus.test.extractor.project.helper.QuarkusTestFramework.isTestFrameworkDependency;
import static io.quarkus.test.extractor.project.result.ParentProject.*;
import static io.quarkus.test.extractor.project.utils.MavenUtils.*;
import static io.quarkus.test.extractor.project.utils.PluginUtils.*;

record ProjectImpl(MavenProject mavenProject, String relativePath, boolean extensionTestModule,
                   ExtractionSummary extractionSummary, String originalProjectName) implements Project {

    private static final String JAVA_FILE_EXTENSION = ".java";
    private static final Path CURRENT_DIR = Path.of(".").toAbsolutePath();
    private static final Set<String> IGNORED_PLUGINS = Set.of("forbiddenapis",
            "templating-maven-plugin", "maven-enforcer-plugin", "impsort-maven-plugin");

    private ProjectImpl(MavenProject mavenProject, String relativePath, ExtractionSummary summary) {
        this(mavenProject, relativePath, isExtensionTestModule(relativePath), summary, mavenProject.getName());
    }

    ProjectImpl(MavenProject mavenProject, ExtractionSummary extractionSummary) {
        this(mavenProject, extractRelativePath(mavenProject), extractionSummary);
    }

    @Override
    public Project parentProject() {
        var parent = mavenProject.getParent();
        return Project.extract(parent, ExtractionSummary.of(parent.getArtifactId()));
    }

    @Override
    public List<Profile> profiles() {
        List<Profile> profiles = new ArrayList<>();
        mavenProject.getOriginalModel().getProfiles().forEach(p -> {
            var profile = p.clone();
            if (profile.getBuild() != null) {
                // TODO: this can be an issue because active profiles in 'mavenProject'
                //   differs from profiles active during a test execution, so basically,
                //   plugins and dependencies declared in inactive profiles are not resolved
                //   one day when we hit issues we need to rewrite this
                // so these are plugins from <build> rather than resolved in the profile
                // my point is that if profiles are not active, they can't have resolved plugin versions, it's not best
                List<Plugin> buildPlugins = mavenProject.getBuildPlugins();
                PluginManagement pluginManagement = mavenProject.getPluginManagement();
                prepareBuild(profile.getBuild(), buildPlugins, pluginManagement, this);
            }
            profile.setDependencyManagement(prepareDependencyManagement(profile.getDependencyManagement(), this));
            var dependencies = profile.getDependencies();
            if (dependencies != null && !dependencies.isEmpty()) {
                var preparedDependencies = new ArrayList<Dependency>();
                dependencies.forEach(d -> {
                    var dependency = d.clone();
                    if (hasThisProjectVersion(dependency)) {
                        dependency.setVersion(null);
                    }
                    if (!isDeploymentArtifact(dependency) && (dependency.getVersion() == null
                            || dependency.getVersion().isEmpty())) {
                        if (!isManagedByQuarkusBom(dependency) && !isManagedByTestParent(dependency)
                                && notAccompaniedWithDeploymentDep(dependency)) {
                            resolveAndSetDependencyVersion(dependency);
                        } else if (isManagedByQuarkusBomButNotProductPlatformBom(dependency)) {
                            setResolvedDependencyVersion(dependency);
                        }
                    }

                    if (hasEmptyVersion(dependency) && isTestJar(dependency)) {
                        setTestJarVersionIfNecessary(dependency, mavenProject);
                    }

                    correctGroupIdIfNecessary(dependency);
                    preparedDependencies.add(dependency);
                });
                preparedDependencies.removeIf(dep -> isQuarkusOwnDependency(dep, version()) && isPomPackageType(dep));
                profile.setDependencies(preparedDependencies);
            }
            profile.setProperties(getProperties(profile.getProperties(), isTestModule()));
            profiles.add(profile);
        });
        return List.copyOf(profiles);
    }

    private void setResolvedDependencyVersion(Dependency dependency) {
        String actualDependencyVersion = findDependencyVersion(dependency);
        dependency.setVersion(actualDependencyVersion);
        extractionSummary.addNotManagedDependency(dependency, this);
    }

    @Override
    public Build build() {
        if (mavenProject.getOriginalModel().getBuild() == null) {
            return null;
        }
        List<Plugin> buildPlugins = mavenProject.getBuildPlugins();
        PluginManagement pluginManagement = mavenProject.getPluginManagement();
        Build build = mavenProject.getOriginalModel().getBuild().clone();
        prepareBuild(build, buildPlugins, pluginManagement, this);
        return build;
    }

    @Override
    public DependencyManagement dependencyManagement() {
        if (!isTestModule()) {
            // don't care, we are not going to use it anyway
            return mavenProject.getDependencyManagement();
        }
        DependencyManagement dependencyManagement = mavenProject.getOriginalModel().getDependencyManagement();
        return prepareDependencyManagement(dependencyManagement, this);
    }

    private DependencyManagement prepareDependencyManagement(DependencyManagement dependencyManagement,
                                                                    Project project) {
        if (dependencyManagement == null || dependencyManagement.getDependencies() == null
                || dependencyManagement.getDependencies().isEmpty()) {
            return null;
        }
        List<Dependency> managedDependencies = dependencyManagement.getDependencies().stream()
                .filter(QuarkusBuildParent::isNotManagedByBuildParent)
                // we don't need Quarkus BOM test parent, and it doesn't exist as we don't build it or keep it
                .filter(d -> !d.getArtifactId().equalsIgnoreCase("quarkus-bom-test"))
                .toList();
        if (!managedDependencies.isEmpty()) {
            managedDependencies.forEach(ParentProject::correctGroupIdIfNecessary);
            managedDependencies.stream().filter(ParentProject::copyAsIsContainsArtifactId).forEach(d -> d.setVersion(version()));
            extractionSummary.addProjectWithDependencyManagement(dependencyManagement, project);
            var clone = dependencyManagement.clone();
            clone.setDependencies(managedDependencies);
            return clone;
        }
        return null;
    }

    @Override
    public List<Dependency> dependencies() {
        var originalDependencies = mavenProject.getOriginalModel().getDependencies();
        if (originalDependencies == null || originalDependencies.isEmpty()) {
            return List.of();
        }
        originalDependencies.removeIf(dep -> {
            // deployment dependencies in IT modules like this:
            // <version>${project.version}</version>
            // <type>pom</type>
            // <scope>test</scope>
            // <exclusions>
            //  <exclusion>
            //  <groupId>*</groupId>
            //  <artifactId>*</artifactId>
            //  </exclusion>
            // </exclusions>
            // they exist to enforce build order, they are not managed (with POM type) and may not be resolved
            // users won't have them in their Quarkus applications, therefore we need to remove them
            // And I have also seen it in at least one extension module when RESTEasy Classic declared such a dependency
            // on quarkus-undertow-deployment, therefore we remove it from everywhere
            // additionally, this removes non-deployment dependencies as well, main motivation was 'quarkus-bom-test'
            // in virtual threads IT module parent, it is added as a dependency (?? order probably) but we really don't
            // want to use their versions, preferably we use RHBQ platform BOM and fallback only for non-managed deps
            return isQuarkusTestScopeDepWithExclusions(dep) && isPomPackageType(dep);
        });

        List<Dependency> result = new ArrayList<>();
        if (extensionTestModule && !isExtensionsSupplementaryModule(relativePath)) {
            // plus: in some cases like Maven invoker tests, this is also used for non-deployment modules
            // not quite sure why, probably same ordering reasons, but that is why we don't test for '-deployment' postfix
            originalDependencies.stream().filter(this::isQuarkusTestScopeDepWithExclusions)
                    .forEach(d -> d.setExclusions(new ArrayList<>()));

            var self = new Dependency();
            self.setGroupId(mavenProject.getGroupId());
            self.setArtifactId(mavenProject.getArtifactId());
            if (!isManagedByQuarkusBom(self)) {
                // ATM at the very least 'quarkus-observability-devservices-deployment' is not managed in '-deployment'
                // module, and it doesn't seem to be an issue, so not a bug
                setQuarkusCoreBomVersion(self);
            }
            result.add(self);
            originalDependencies.forEach(dep -> {
                var dependency = dep.clone();
                // some test scope dependencies probably are not managed by Quarkus BOM
                // but are managed due to Quarkus Build Parent dependency management
                // however we only use delivered artifacts and use Quarkus platform BOM
                // for testing so we need to set the version explicitly
                if (dependency.getVersion() == null || dependency.getVersion().isEmpty()) {
                    if (isDeploymentArtifact(dependency) && isTestJar(dependency)) {
                        // deployment module signals it is Quarkus core extension module
                        // and when it is a test jar, it is not managed (so far I didn't see Quarkus BOM to manage it)
                        // so let's just use community Quarkus BOM version because that should fit
                        // also, test-jars are not productized
                        setQuarkusCommunityVersion(dependency);
                        extractionSummary.addNotManagedDependency(dependency, this, QUARKUS_COMMUNITY_VERSION_REF);
                    } else if (!isManagedByQuarkusBom(dependency) && !isManagedByTestParent(dependency)) {
                        resolveAndSetDependencyVersion(dependency);
                    } else if (isManagedByQuarkusBomButNotProductPlatformBom(dependency)) {
                        setResolvedDependencyVersion(dependency);
                    }
                }
                if (hasThisProjectVersion(dependency)) {
                    dependency.setVersion(null);
                    if (!isManagedByQuarkusBom(dependency)) {
                        if (COMMUNITY_DEPENDENCIES.contains(dependency.getArtifactId())) {
                            setQuarkusCommunityVersion(dependency);
                        } else {
                            setQuarkusCoreBomVersion(dependency);
                        }
                    }
                }

                if (hasEmptyVersion(dependency) && isTestJar(dependency)) {
                    setTestJarVersionIfNecessary(dependency, mavenProject);
                }

                result.add(dependency);
            });
            result.forEach(ParentProject::correctGroupIdIfNecessary);
            // each '-deployment' artifact without test scope requires runtime counterpart
            var runtimeCounterparts = result.stream()
                    .filter(d -> !hasTestScope(d))
                    .filter(PluginUtils::isDeploymentArtifact)
                    .filter(d2 -> !isTestJar(d2))
                    .filter(d2 -> !MavenUtils.isPomPackageType(d2))
                    .filter(d -> !d.getArtifactId().contains("-spi"))
                    // exception, this doesn't have nor need runtime counterpart
                    .filter(d -> !d.getArtifactId().equals("quarkus-devservices-deployment"))
                    .map(d -> {
                        Dependency runtimeDependency = d.clone();
                        runtimeDependency.setArtifactId(dropDeploymentPostfix(runtimeDependency.getArtifactId()));
                        return runtimeDependency;
                    })
                    .filter(d -> CoreExtensions.isCoreExtension(d.getArtifactId()))
                    .filter(d -> {
                        String runtimeArtifactId = d.getArtifactId();
                        return result.stream()
                                .filter(d2 -> !isTestJar(d2))
                                .filter(d2 -> !MavenUtils.isPomPackageType(d2))
                                .noneMatch(d2 -> d2.getArtifactId().equalsIgnoreCase(runtimeArtifactId));
                    })
                    .collect(Collectors.toSet());
            result.addAll(runtimeCounterparts);
        } else {
            // IT modules may contain even POM type dependencies without exclusions or a test scope
            // in some cases they are not resolvable, like 'io.quarkus.gradle.plugin' in the
            // 'quarkus-integration-test-gradle-plugin' integration module
            originalDependencies.removeIf(dep -> isQuarkusOwnDependency(dep, version()) && isPomPackageType(dep));

            originalDependencies.forEach(dep -> {
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
                    } else if (isManagedByQuarkusBomButNotProductPlatformBom(dependency)) {
                        setResolvedDependencyVersion(dependency);
                    }
                }
                if (hasThisProjectVersion(dependency)) {
                    dependency.setVersion(null);
                    if (!isManagedByQuarkusBom(dependency)) {
                        if (COMMUNITY_DEPENDENCIES.contains(dependency.getArtifactId())) {
                            setQuarkusCommunityVersion(dependency);
                        } else if ("quarkus-maven-plugin".equalsIgnoreCase(dependency.getArtifactId())
                                || "quarkus-bom-quarkus-platform-descriptor".equalsIgnoreCase(dependency.getArtifactId())) {
                            setQuarkusPlatformVersion(dependency);
                        } else {
                            setQuarkusCoreBomVersion(dependency);
                        }
                    }
                }

                if (hasEmptyVersion(dependency) && isTestJar(dependency)) {
                    setTestJarVersionIfNecessary(dependency, mavenProject);
                }

                result.add(dependency);
            });
            result.forEach(ParentProject::correctGroupIdIfNecessary);
        }
        result.stream().filter(ParentProject::copyAsIsContainsArtifactId).forEach(d -> d.setVersion(null));
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
                extractionSummary.addRepository(repository, this);
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
                extractionSummary.addPluginRepository(repository, this);
                result.add(repository);
            }
        });
        return List.copyOf(result);
    }

    private boolean isQuarkusTestScopeDepWithExclusions(Dependency dep) {
        // else if extension because quarkus-undertow-deployment in quarkus-resteasy-deployment
        // has wrong scope in model but not in POM for whatever reason
        return (hasTestScope(dep) || (extensionTestModule && !isExtensionsSupplementaryModule(relativePath)))
                && isQuarkusOwnDependency(dep, version())
                && dep.getExclusions().size() == 1
                && ANY.equalsIgnoreCase(dep.getExclusions().get(0).getGroupId())
                && ANY.equalsIgnoreCase(dep.getExclusions().get(0).getArtifactId());
    }

    private static boolean isQuarkusOwnDependency(Dependency dep, String projectVersion) {
        String artifactId = dep.getArtifactId();
        return (artifactId.startsWith("quarkus-") || artifactId.startsWith("io.quarkus"))
                && (dep.getVersion() == null || dep.getVersion().isEmpty()
                || hasThisProjectVersion(dep) || projectVersion.equalsIgnoreCase(dep.getVersion()));
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
        if (extensionTestModule && !isExtensionsSupplementaryModule(relativePath)) {
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
        // so that we don't make ourselves problems, the project name must stay same
        // one example: FlywayDevModeCreateFromHibernateTest in the flyway extension deployment module
        // expected V1.0.0__quarkus-flyway-deployment.sql file to exist, because Flyway extension creates
        // a migration file name like this: "V1.0.0__" + artifactId + ".sql"
        return mavenProject.getArtifactId();
    }

    @Override
    public String targetRelativePath() {
        // extensions/vertx-http/deployment -> extensions/vertx-http
        if (extensionTestModule) {
            var extensionParentProject = findExtensionParent(mavenProject);
            Path mavenProjectPath = mavenProject.getBasedir().toPath().toAbsolutePath();
            var parent = mavenProjectPath.getParent();
            var newDirName = parent.getFileName() + "-" + mavenProjectPath.getFileName();
            var newDirPath = extensionParentProject.getBasedir().toPath().toAbsolutePath().resolve(newDirName);
            return CURRENT_DIR.relativize(newDirPath).toString();
        }
        return relativePath;
    }

    private static MavenProject findExtensionParent(MavenProject mavenProject) {
        var project = mavenProject;
        while (project.getParent() != null) {
            project = project.getParent();
            if ("quarkus-extensions-parent".equalsIgnoreCase(project.getArtifactId())) {
                return project;
            }
        }
        throw new IllegalStateException("Project with artifact id 'quarkus-extensions-parent' not found is not"
                + " a parent module of project " + mavenProject.getName() + " with path "
                + mavenProject.getBasedir().toPath().toAbsolutePath());
    }

    @Override
    public boolean isDirectSubModule() {
        if (extensionTestModule) {
            return true;
        }
        return IntegrationTestModules.isDirectItModule(relativePath);
    }

    @Override
    public boolean isTestModule() {
        // it is reasonable to say that integration test modules either contains tests or other modules needs them
        if (extensionTestModule) {
            return containsTests();
        } else return isIntegrationTestModule();
    }

    @Override
    public boolean containsTests() {
        Path sourceProjectSrcTestJavaPath = projectPath().resolve("src").resolve("test").resolve("java");
        if (Files.exists(sourceProjectSrcTestJavaPath)) {
            try(var pathStream = Files.walk(sourceProjectSrcTestJavaPath)) {
                return pathStream.anyMatch(p -> p.toString().endsWith(JAVA_FILE_EXTENSION));
            } catch (IOException e) {
                throw new RuntimeException("Failed to detect whether '%s' extension module contains test "
                        .formatted(sourceProjectSrcTestJavaPath), e);
            }
        }
        return false;
    }

    @Override
    public Properties properties() {
        final Properties mavenProjectProperties = mavenProject.getProperties();
        return getProperties(mavenProjectProperties, isTestModule());
    }

    @Override
    public String targetProfileName() {
        return relativePath().startsWith(EXTENSIONS) ? EXTENSIONS : INTEGRATION_TESTS + getProfilePostfix(this);
    }

    @Override
    public Model originalModel() {
        var model = mavenProject.getOriginalModel().clone();
        model.setBuild(build());
        model.setDependencies(dependencies());
        model.setDependencyManagement(prepareDependencyManagement(model.getDependencyManagement(), this));
        model.setProfiles(profiles());
        return model;
    }

    @Override
    public String packagingType() {
        return mavenProject.getPackaging();
    }

    @Override
    public boolean isIntegrationTestModule() {
        return relativePath.startsWith(INTEGRATION_TESTS + File.separator);
    }

    @Override
    public Path projectPath() {
        return CURRENT_DIR.resolve(relativePath);
    }

    private void resolveAndSetDependencyVersion(Dependency dependency) {
        String actualDependencyVersion = findDependencyVersion(dependency);
        if (isTestFrameworkDependency(dependency)) {
            // some test framework dependencies are not managed by Quarkus BOM
            setQuarkusCommunityVersion(dependency);
            extractionSummary.addNotManagedDependency(dependency, this, QUARKUS_COMMUNITY_VERSION_REF);
        } else if (isProductizedButNotManaged(dependency)) {
            setQuarkusCoreBomVersion(dependency);
            extractionSummary.addNotManagedDependency(dependency, this, QUARKUS_CORE_BOM_VERSION_REF);
        } else if (COMMUNITY_DEPENDENCIES.contains(dependency.getArtifactId())) {
            setQuarkusCommunityVersion(dependency);
            extractionSummary.addNotManagedDependency(dependency, this, QUARKUS_COMMUNITY_VERSION_REF);
        } else if (actualDependencyVersion == null) {
            setQuarkusCoreBomVersion(dependency);
            extractionSummary.addNotManagedDependency(dependency, this, QUARKUS_CORE_BOM_VERSION_REF);
        } else if (actualDependencyVersion.equalsIgnoreCase(version())) {
            setQuarkusCommunityVersion(dependency);
            extractionSummary.addNotManagedDependency(dependency, this, QUARKUS_COMMUNITY_VERSION_REF);
        } else {
            dependency.setVersion(actualDependencyVersion);
            extractionSummary.addNotManagedDependency(dependency, this);
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

    private void prepareBuild(BuildBase build, List<Plugin> buildPlugins, PluginManagement pluginManagement,
                                     Project project) {
        if (build.getPlugins() != null) {
            build.getPlugins().removeIf(plugin -> {
                if (IGNORED_PLUGINS.contains(plugin.getArtifactId())) {
                    return true;
                }
                if ("maven-compiler-plugin".equalsIgnoreCase(plugin.getArtifactId())) {
                    plugin.setVersion("$USE-EXTRACTED-PROPERTIES{version.compiler.plugin}");
                    // if it is only plugin with no configuration, we don't need it
                    // if only 'quarkus-extension-processor' annotation processor is present
                    // we don't want it, otherwise, we need to copy modified plugin
                    boolean noDeps = plugin.getDependencies() == null || plugin.getDependencies().isEmpty();
                    boolean noConfig = plugin.getConfiguration() == null;
                    boolean noExecutions = plugin.getExecutions() == null || plugin.getExecutions().isEmpty();
                    if (noDeps && noConfig && noExecutions) {
                        return true;
                    } else {
                        // that's because this extension module now has '-AgenerateDoc=false' compiler args
                        // in the default profile and I want to keep them
                        boolean isNotItExtensionExtModule = !targetRelativePath().contains("integration-tests/test-extension/extension/");
                        if (isNotItExtensionExtModule) {
                            if (plugin.getExecutions().size() == 1) {
                                var pluginExecution = plugin.getExecutions().get(0);
                                return "default-compile".equalsIgnoreCase(pluginExecution.getId());
                            }
                        }
                    }
                }
                if ("quarkus-extension-maven-plugin".equalsIgnoreCase(plugin.getArtifactId())) {
                    boolean noDeps = plugin.getDependencies() == null || plugin.getDependencies().isEmpty();
                    boolean noConfig = plugin.getConfiguration() == null;
                    boolean noExecutions = plugin.getExecutions() == null || plugin.getExecutions().isEmpty();
                    if (noDeps && noConfig && noExecutions) {
                        return true;
                    }
                }
                return false;
            });
            if (!build.getPlugins().isEmpty()) {
                build.getPlugins().forEach(plugin -> {
                    // we manage failsafe and surefire plugins because it's given we need them
                    // as for others, they are not managed so that we still know they are needed,
                    // and we record that need in the extraction summary; we need to understand
                    // what plugins are used as inspecting a thousand of modules is impossible
                    if ((plugin.getVersion() == null) && isNotSurefireOrFailsafePlugin(plugin.getArtifactId())) {
                        String pluginVersion = null;
                        if (!isQuarkusParentPomProject(project)) {
                            pluginVersion = QuarkusParentPom.getPluginVersion(plugin);
                        }
                        if (pluginVersion == null) {
                            pluginVersion = getPluginVersionInParentProps(plugin);
                        }
                        if (pluginVersion == null) {
                            // some versions are just in other BOM, it can be really complicated hierarchy
                            // let's just use resolved version
                            pluginVersion = findResolvedPluginVersion(plugin, buildPlugins, pluginManagement);
                        }
                        plugin.setVersion(pluginVersion);
                    }
                    if (PluginUtils.isQuarkusMavenPlugin(plugin.getArtifactId(), plugin.getGroupId())) {
                        // RHBQ uses productized plugin and the group id is 'com.redhat.quarkus.platform'
                        // so make the group id configurable
                        plugin.setGroupId("$" + USE_EXTRACTED_PROPERTIES + "{" + QUARKUS_PLATFORM_GROUP_ID + "}");
                        plugin.setVersion("$" + USE_EXTRACTED_PROPERTIES + "{" + QUARKUS_PLATFORM_VERSION + "}");
                    }
                    extractionSummary.addBuildPlugin(plugin, project);
                });
            }
            if (build.getPluginManagement() != null && build.getPluginManagement().getPlugins() != null) {
                build.getPluginManagement().getPlugins().forEach(plugin -> {
                    if (PluginUtils.isQuarkusMavenPlugin(plugin.getArtifactId(), plugin.getGroupId())) {
                        // RHBQ uses productized plugin and the group id is 'com.redhat.quarkus.platform'
                        // so make the group id configurable
                        plugin.setGroupId("$" + USE_EXTRACTED_PROPERTIES + "{" + QUARKUS_PLATFORM_GROUP_ID + "}");
                        plugin.setVersion("$" + USE_EXTRACTED_PROPERTIES + "{" + QUARKUS_PLATFORM_VERSION + "}");
                    }
                });
            }
        }
    }

    private static String findResolvedPluginVersion(Plugin plugin, List<Plugin> buildPlugins,
                                                    PluginManagement pluginManagement) {
        String pluginVersion = null;
        if (buildPlugins != null && !buildPlugins.isEmpty()) {
            pluginVersion = buildPlugins.stream()
                    .filter(p -> plugin.getArtifactId().equalsIgnoreCase(p.getArtifactId()))
                    .map(Plugin::getVersion)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        if (pluginVersion != null) {
            return pluginVersion;
        } else if (pluginManagement != null
                && pluginManagement.getPlugins() != null) {
            pluginVersion = pluginManagement.getPlugins().stream()
                    .filter(p -> plugin.getArtifactId().equalsIgnoreCase(p.getArtifactId()))
                    .map(Plugin::getVersion)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        if (pluginVersion != null) {
            return pluginVersion;
        }
        // this is the last resort method, so now someone has to manually lookup where is it managed and change impl.
        // it is possible that some plugins only present in profiles will not appear in the build plugins, and we just
        // need to walk through profile plugins
        throw new IllegalStateException("Failed to determine plugin '%s' version".formatted(plugin.getArtifactId()));
    }

    private static Properties getProperties(Properties mavenProjectProperties, boolean isTestModule) {
        if (mavenProjectProperties != null && isTestModule) {
            Properties testModuleProperties = new Properties();
            mavenProjectProperties.forEach((k, v) -> {
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
        if (mavenProjectProperties != null) {
            properties.putAll(mavenProjectProperties);
        }
        return properties;
    }

    private static boolean isManagedByQuarkusBomButNotProductPlatformBom(Dependency dependency) {
        // ATM org.bouncycastle:bctls-jdk18on is only used by ITs and in docs, so this BC dependency is not productized
        // and not managed by product platform BOM
        return "org.bouncycastle".equalsIgnoreCase(dependency.getGroupId())
                && "bctls-jdk18on".equalsIgnoreCase(dependency.getArtifactId());
    }
}
