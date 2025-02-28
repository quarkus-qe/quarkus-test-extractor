package io.quarkus.test.extractor.project.helper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static io.quarkus.test.extractor.project.utils.PluginUtils.TARGET_DIR;

final class FileSystemStorage {

    static boolean exists(String fileName) {
        return Files.exists(TARGET_DIR.resolve(fileName));
    }

    static String loadFileContent(String fileName) {
        Path filePath = TARGET_DIR.resolve(fileName);
        try {
            return Files.readString(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + filePath, e);
        }
    }

    static void saveFileContent(String fileName, String content) {
        Path filePath = TARGET_DIR.resolve(fileName);
        try {
            Files.writeString(filePath, content, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save content to " + fileName, e);
        }
    }

}
