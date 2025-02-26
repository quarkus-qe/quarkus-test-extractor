package io.quarkus.test.extractor.plugin;

import io.quarkus.test.extractor.project.helper.QuarkusBom;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static org.apache.maven.plugins.annotations.LifecyclePhase.PACKAGE;
import static org.apache.maven.plugins.annotations.ResolutionScope.COMPILE;

@Mojo(name = "parse-quarkus-bom", defaultPhase = PACKAGE, requiresDependencyCollection = COMPILE, requiresDependencyResolution = COMPILE, threadSafe = true)
public class ParseQuarkusBomMojo extends AbstractMojo {

    private static final String QUARKUS_BOM_ARTIFACT_ID = "quarkus-bom";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject mavenProject;

    @Override
    public void execute() throws MojoExecutionException {
        if (!QUARKUS_BOM_ARTIFACT_ID.equalsIgnoreCase(mavenProject.getArtifactId())) {
            throw new MojoExecutionException("This mojo is only applicable to the 'quarkus-bom' artifact");
        }
        QuarkusBom.saveDependencyKeys(mavenProject);
    }

}
