package io.quarkus.test.extractor.project.helper;

import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;

import java.util.Map;

public final class KnownTestJars {

    /**
     * Group id to artifact id of test jar artifacts that should use community version because they do not exist
     * as test-jars in RHBQ.
     */
    private static final Map<String, String> COMMUNITY_TEST_JARS = Map.of("io.quarkus.gizmo", "gizmo");

    public static void setTestJarVersionIfNecessary(Dependency dependency, MavenProject project) {
        var groupId = dependency.getGroupId();
        if (groupId != null && dependency.getArtifactId() != null) {
            var artifactId = COMMUNITY_TEST_JARS.get(groupId);
            if (artifactId != null && artifactId.equalsIgnoreCase(dependency.getArtifactId())) {
                // resolve actual version and hardcode it
                project.getDependencies().stream()
                        .filter(d -> groupId.equalsIgnoreCase(d.getGroupId()))
                        .filter(d -> artifactId.equalsIgnoreCase(d.getArtifactId()))
                        .findFirst()
                        .ifPresent(d -> dependency.setVersion(d.getVersion()));
            }
        }
    }
}
