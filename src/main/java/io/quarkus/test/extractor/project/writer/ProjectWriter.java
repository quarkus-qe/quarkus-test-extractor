package io.quarkus.test.extractor.project.writer;

import io.quarkus.test.extractor.project.builder.Project;
import io.quarkus.test.extractor.project.helper.ExtractionSummary;

public sealed interface ProjectWriter permits ProjectWriterImpl {

    void writeProject(Project project);

    static void writeProject(Project project, ExtractionSummary extractionSummary) {
        ProjectWriter projectWriter = new ProjectWriterImpl(extractionSummary);
        projectWriter.writeProject(project);
    }

}
