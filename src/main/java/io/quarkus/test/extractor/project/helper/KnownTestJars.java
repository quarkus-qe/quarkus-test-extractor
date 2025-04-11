package io.quarkus.test.extractor.project.helper;

import org.apache.maven.model.Dependency;

import java.util.Map;

public final class KnownTestJars {

    /**
     * Group id to artifact id of test jar artifacts that should use community version because they do not exist
     * as test-jars in RHBQ.
     */
    private static final Map<String, String> COMMUNITY_TEST_JARS = Map.of("io.quarkus.gizmo", "gizmo");

    public static void setTestJarVersionIfNecessary(Dependency dependency) {
        if (dependency.getGroupId() != null && dependency.getArtifactId() != null) {
            var artifactId = COMMUNITY_TEST_JARS.get(dependency.getGroupId());
            if (artifactId != null && artifactId.equalsIgnoreCase(dependency.getArtifactId())) {
                dependency.setVersion("$USE-EXTRACTED-PROPERTIES{community.quarkus.version}");
            }
        }
    }
}
