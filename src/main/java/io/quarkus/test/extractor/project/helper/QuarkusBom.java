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

    private static final String MANAGED_DEPENDENCIES_FILE_NAME = "quarkus-bom-managed-deps";
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
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return managementKeys;
    }

    private static void saveToFileSystem(String managementKeys) throws MojoExecutionException {
        try {
            Files.writeString(getManagementKeysPath(), managementKeys, CREATE_NEW);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to save Quarkus BOM managed dependencies. "
                    + "Please make sure that target directory does "
                    + "not exist or is empty", e);
        }
    }

    private static Path getManagementKeysPath() {
        return PluginUtils.TARGET_DIR.resolve(MANAGED_DEPENDENCIES_FILE_NAME);
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
        saveToFileSystem(getManagementKeys(mavenProject));
    }

    public static boolean isManagedByQuarkusBom(Dependency dependency) {
        return INSTANCE.isManagedByQuarkusBomInternal(dependency);
    }
}
