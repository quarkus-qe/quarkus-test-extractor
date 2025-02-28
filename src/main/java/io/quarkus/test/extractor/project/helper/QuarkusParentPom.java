package io.quarkus.test.extractor.project.helper;

import io.quarkus.test.extractor.project.builder.Project;
import org.apache.maven.model.Plugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static io.quarkus.test.extractor.project.helper.FileSystemStorage.exists;
import static io.quarkus.test.extractor.project.helper.FileSystemStorage.loadFileContent;
import static io.quarkus.test.extractor.project.helper.FileSystemStorage.saveFileContent;
import static io.quarkus.test.extractor.project.utils.PluginUtils.isQuarkusParentPomProject;

public record QuarkusParentPom(Map<String, String> pluginToVersion) {

    private static final String ENTRY_SEPARATOR = ";";
    private static final String QUARKUS_PARENT_POM_FILE_NAME = "quarkus-parent-pom-context";
    private static final QuarkusParentPom INSTANCE = load();

    public static void collectPluginVersions(Project project) {
        if (!isQuarkusParentPomProject(project)) {
            throw new IllegalArgumentException("Only Quarkus Parent project is supported");
        }
        var properties = project.properties();
        if (project.build() != null) {
            var build = project.build();
            if (build.getPlugins() != null) {
                build.getPlugins()
                        .stream()
                        .filter(p -> p.getVersion() != null)
                        .forEach(p -> getPluginToVersion().put(getArtifactId(p), resolveVersion(p, properties)));
            }
            if (build.getPluginManagement() != null && build.getPluginManagement().getPlugins() != null) {
                build.getPluginManagement().getPlugins()
                        .stream()
                        .filter(p -> p.getVersion() != null)
                        .forEach(p -> getPluginToVersion().put(getArtifactId(p), resolveVersion(p, properties)));
            }
        }
        if (!getPluginToVersion().isEmpty()) {
            saveFileContent(QUARKUS_PARENT_POM_FILE_NAME, serializeAsString());
        }
    }

    public static String getPluginVersion(Plugin plugin) {
        return getPluginToVersion().get(getArtifactId(plugin));
    }

    private static String getArtifactId(Plugin plugin) {
        return plugin.getArtifactId().trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> getPluginToVersion() {
        return INSTANCE.pluginToVersion();
    }

    private static QuarkusParentPom load() {
        if (exists(QUARKUS_PARENT_POM_FILE_NAME)) {
            String content = loadFileContent(QUARKUS_PARENT_POM_FILE_NAME);
            Map<String, String> pluginToVersion = content.lines()
                    .filter(l -> l != null && !l.isBlank())
                    .map(String::trim)
                    .map(line -> line.split(ENTRY_SEPARATOR))
                    .collect(Collectors.toUnmodifiableMap(a -> a[0], a -> a[1]));
            return new QuarkusParentPom(pluginToVersion);
        } else {
            return new QuarkusParentPom(new HashMap<>());
        }
    }

    private static String resolveVersion(Plugin p, Properties properties) {
        String version = p.getVersion();
        if (version.startsWith("${") && version.endsWith("}")) {
            String propertyName = version.substring(2, version.length() - 1);
            version = properties.getProperty(propertyName);
        }
        return version;
    }

    private static String serializeAsString() {
        return getPluginToVersion().entrySet().stream()
                .map(e -> e.getKey() + ENTRY_SEPARATOR + e.getValue())
                .collect(Collectors.joining(System.lineSeparator()));
    }
}
