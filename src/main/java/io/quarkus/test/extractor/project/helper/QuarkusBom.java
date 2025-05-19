package io.quarkus.test.extractor.project.helper;

import io.quarkus.test.extractor.project.utils.MavenUtils;
import io.quarkus.test.extractor.project.utils.PluginUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.quarkus.test.extractor.project.utils.MavenUtils.getManagementKey;
import static io.quarkus.test.extractor.project.utils.PluginUtils.createDirectoryStructureIfNotExists;
import static java.nio.file.StandardOpenOption.CREATE_NEW;

public final class QuarkusBom {

    private static final String QUARKUS_BOM_ARTIFACT_ID = "quarkus-bom";
    private static final String MANAGED_DEPENDENCIES_FILE_NAME = "quarkus-bom-managed-deps";
    private static final String STORK_CONFIG_GEN_VERSION_FILE_NAME = "stork-configuration-generator-version";
    private static final String HIBERNATE_JPA_MODEL_GEN_VERSION_FILE_NAME = "hibernate-jpamodelgen-version";
    private static final QuarkusBom INSTANCE = create();
    private final Set<String> managementKeys;
    private volatile boolean validated = false;

    private QuarkusBom(Set<String> managementKeys) {
        this.managementKeys = Set.copyOf(managementKeys);
    }

    private boolean isManagedByQuarkusBomInternal(Dependency dependency) {
        validateQuarkusBomState();
        if (dependency == null || dependency.getManagementKey() == null) {
            return false;
        }
        return managementKeys.contains(getManagementKey(dependency));
    }

    private void validateQuarkusBomState() {
        if (!validated) {
            // basically we don't know which mojo this is until someone tries to
            // use this bean, so perform validation once lazily
            synchronized (this) {
                if (!validated) {
                    if (managementKeys.isEmpty()) {
                        throw new IllegalStateException("Found no dependencies managed by Quarkus BOM, this probably means that "
                                + "'parse-quarkus-bom' mojo wasn't executed prior to this call");
                    }
                    validated = true;
                }
            }
        }
    }

    private static QuarkusBom create() {
        if (Files.exists(getManagementKeysPath())) {
            // extract tests mojo
            return new QuarkusBom(loadManagementKeys());
        } else {
            // parse quarkus bom mojo
            return new QuarkusBom(Set.of());
        }
    }

    private static Set<String> loadManagementKeys() {
        Set<String> managementKeys;
        try {
            managementKeys = Files.readString(getManagementKeysPath())
                    .lines()
                    .map(String::trim)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return managementKeys;
    }

    private static void saveToFileSystem(String managementKeys, String storkConfigGenVersion,
                                         String hibernateJpaModelGenVersion) throws MojoExecutionException {
        try {
            Files.writeString(getManagementKeysPath(), managementKeys, CREATE_NEW);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to save Quarkus BOM managed dependencies. "
                    + "Please make sure that target directory does "
                    + "not exist or is empty", e);
        }
        try {
            Files.writeString(getStorkConfigGenVersionPath(), storkConfigGenVersion, CREATE_NEW);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to save 'stork-configuration-generator' version. "
                    + "Please make sure that target directory does "
                    + "not exist or is empty", e);
        }
        try {
            Files.writeString(getHibernateJpaModelGenVersionPath(), hibernateJpaModelGenVersion, CREATE_NEW);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to save 'hibernate-jpamodelgen' version. "
                    + "Please make sure that target directory does "
                    + "not exist or is empty", e);
        }
    }

    private static Path getManagementKeysPath() {
        return PluginUtils.TARGET_DIR.resolve(MANAGED_DEPENDENCIES_FILE_NAME);
    }

    private static Path getStorkConfigGenVersionPath() {
        return PluginUtils.TARGET_DIR.resolve(STORK_CONFIG_GEN_VERSION_FILE_NAME);
    }

    private static Path getHibernateJpaModelGenVersionPath() {
        return PluginUtils.TARGET_DIR.resolve(HIBERNATE_JPA_MODEL_GEN_VERSION_FILE_NAME);
    }

    private static String getManagementKeys(MavenProject mavenProject) {
        if (mavenProject.getDependencyManagement() == null) {
            throw new IllegalStateException("Maven project has no dependency management");
        }
        Stream<Dependency> dependencies = mavenProject.getDependencyManagement().getDependencies().stream();
        if (mavenProject.getDependencies() != null) {
            dependencies = Stream.concat(dependencies, mavenProject.getDependencies().stream());
        }
        return dependencies.map(MavenUtils::getManagementKey).collect(Collectors.joining(System.lineSeparator()));
    }

    public static void saveDependencyKeys(MavenProject mavenProject) throws MojoExecutionException {
        createDirectoryStructureIfNotExists();
        saveToFileSystem(getManagementKeys(mavenProject),
                getManagedArtifactVersion(mavenProject, "io.smallrye.stork", "stork-configuration-generator"),
                getManagedArtifactVersion(mavenProject, "org.hibernate.orm", "hibernate-jpamodelgen"));
    }

    private static String getManagedArtifactVersion(MavenProject mavenProject, String groupId, String artifactId) {
        return mavenProject.getDependencyManagement().getDependencies().stream()
                .filter(d -> artifactId.equalsIgnoreCase(d.getArtifactId())
                        && groupId.equalsIgnoreCase(d.getGroupId()))
                .findFirst()
                .orElseThrow()
                .getVersion();
    }

    public static boolean isQuarkusBom(String artifactId) {
        return QUARKUS_BOM_ARTIFACT_ID.equalsIgnoreCase(artifactId);
    }

    public static boolean isManagedByQuarkusBom(Dependency dependency) {
        return INSTANCE.isManagedByQuarkusBomInternal(dependency);
    }

    public static String getVersionForDependencyKey(String depManagementKey) {
        if ("io.smallrye.stork:stork-configuration-generator".equalsIgnoreCase(depManagementKey)) {
            return loadPath(getStorkConfigGenVersionPath());
        } if ("org.hibernate.orm:hibernate-jpamodelgen".equalsIgnoreCase(depManagementKey)) {
            return loadPath(getHibernateJpaModelGenVersionPath());
        }
        throw new IllegalArgumentException("Unsupported dependency key: " + depManagementKey);
    }

    private static String loadPath(Path path) {
        try {
            return Files.readString(path).trim();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
