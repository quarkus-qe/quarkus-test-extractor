package io.quarkus.test.extractor.plugin;

import io.quarkus.test.extractor.project.helper.QuarkusBom;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static io.quarkus.test.extractor.project.helper.CoreExtensions.addIfCoreExtension;
import static io.quarkus.test.extractor.project.helper.IntegrationTestModules.addDirectItModules;
import static io.quarkus.test.extractor.project.helper.IntegrationTestModules.isItModuleParent;
import static io.quarkus.test.extractor.project.helper.QuarkusBom.isQuarkusBom;
import static org.apache.maven.plugins.annotations.LifecyclePhase.PACKAGE;
import static org.apache.maven.plugins.annotations.ResolutionScope.COMPILE;

@Mojo(name = "collect-project-metadata", defaultPhase = PACKAGE, requiresDependencyCollection = COMPILE, requiresDependencyResolution = COMPILE, threadSafe = true)
public class CollectProjectMetadataMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject mavenProject;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            collectProjectMetadata(mavenProject);
        } catch (Exception e) {
            throw new MojoExecutionException("Mojo 'collect-project-metadata' execution failed", e);
        }
    }

    private static void collectProjectMetadata(MavenProject mavenProject) throws MojoExecutionException {
        if (isQuarkusBom(mavenProject.getArtifactId())) {
            QuarkusBom.saveDependencyKeys(mavenProject);
        } else if (isItModuleParent(mavenProject.getArtifactId())) {
            addDirectItModules(mavenProject.getOriginalModel());
        } else {
            addIfCoreExtension(mavenProject.getArtifactId(), mavenProject.getBasedir().getAbsolutePath());
        }
    }

}
