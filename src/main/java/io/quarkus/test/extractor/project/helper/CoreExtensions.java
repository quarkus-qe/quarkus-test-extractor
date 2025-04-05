package io.quarkus.test.extractor.project.helper;

import java.util.Set;

import static java.util.stream.Collectors.toUnmodifiableSet;

public class CoreExtensions {

    private static final String CORE_EXTENSION_FILE_NAME = "core-extensions";
    private static volatile Set<String> coreExtensions = null;

    /**
     * @return artifact ids of core extensions
     */
    private static Set<String> getCoreExtensions() {
        if (coreExtensions == null) {
            String extensions = FileSystemStorage.loadFileContent(CORE_EXTENSION_FILE_NAME);
            if (extensions.isEmpty()) {
                throw new IllegalStateException(
                        "No core extensions found, please run 'collect-project-metadata' goal first");
            }
            coreExtensions = extensions.lines().map(String::trim).filter(s -> !s.isEmpty()).collect(toUnmodifiableSet());
        }
        return coreExtensions;
    }

    public static boolean isCoreExtension(String artifactId) {
        return getCoreExtensions().contains(artifactId);
    }

    public static void addIfCoreExtension(String artifactId, String moduleBasePath) {
        if (artifactId == null || artifactId.isEmpty()) {
            return;
        }
        if (moduleBasePath.contains("/extensions/") && !"quarkus-extensions-parent".equalsIgnoreCase(artifactId)) {
            FileSystemStorage.addToFile(CORE_EXTENSION_FILE_NAME, artifactId);
        }
    }

}
