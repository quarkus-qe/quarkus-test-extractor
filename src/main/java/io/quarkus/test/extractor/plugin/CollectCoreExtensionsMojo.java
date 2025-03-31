package io.quarkus.test.extractor.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static io.quarkus.test.extractor.project.helper.CoreExtensions.addIfCoreExtension;
import static org.apache.maven.plugins.annotations.LifecyclePhase.PACKAGE;
import static org.apache.maven.plugins.annotations.ResolutionScope.COMPILE;

@Mojo(name = "collect-core-extensions", defaultPhase = PACKAGE, requiresDependencyCollection = COMPILE, requiresDependencyResolution = COMPILE, threadSafe = true)
public class CollectCoreExtensionsMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject mavenProject;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            addIfCoreExtension(mavenProject.getArtifactId(), mavenProject.getBasedir().getAbsolutePath());
        } catch (Exception e) {
            throw new MojoExecutionException("Mojo 'extract-tests' execution failed", e);
        }
    }

}
