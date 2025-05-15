package io.quarkus.test.extractor.project.helper;

import io.quarkus.test.extractor.project.builder.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import static io.quarkus.test.extractor.project.utils.PluginUtils.getTargetProjectDirPath;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

public record FileChanger(Project project, String filePath) {

    void changeContent(Function<String, String> replacement) {
        // this is the right way to get path for ITs, I didn't try it for extension modules
        var absolutePath = getTargetProjectDirPath(project).resolve(filePath);
        changeContent(replacement, absolutePath);
    }

    public static void changeContent(Function<String, String> replacement, Path absolutePath) {
        if (Files.exists(absolutePath)) {
            try {
                String testContent = Files.readString(absolutePath);
                Files.writeString(absolutePath, replacement.apply(testContent), TRUNCATE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("Failed to adjust file " + absolutePath, e);
            }
        } else {
            // this is not super important, but we should at least warn
            System.err.println("Failed to find file " + absolutePath + " which means implementation has changed");
        }
    }
}
