package io.quarkus.test.extractor.plugin;

import io.quarkus.test.extractor.project.builder.Project;
import io.quarkus.test.extractor.project.writer.ProjectWriter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static org.apache.maven.plugins.annotations.LifecyclePhase.PACKAGE;
import static org.apache.maven.plugins.annotations.ResolutionScope.COMPILE;

@Mojo(name = "extract-tests", defaultPhase = PACKAGE, requiresDependencyCollection = COMPILE, requiresDependencyResolution = COMPILE, threadSafe = true)
public class ExtractTestsMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject mavenProject;

    @Override
    public void execute() {
        ProjectWriter.write(Project.extract(mavenProject));
    }

}
