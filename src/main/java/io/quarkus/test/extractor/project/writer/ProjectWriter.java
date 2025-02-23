package io.quarkus.test.extractor.project.writer;

import io.quarkus.test.extractor.project.builder.Project;

public sealed interface ProjectWriter permits ProjectWriterImpl {

    static void write(Project project) {
        ProjectWriterImpl.getInstance().writeProject(project);
    }

    void writeProject(Project project);

}
