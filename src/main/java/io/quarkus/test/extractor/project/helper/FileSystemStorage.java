package io.quarkus.test.extractor.project.helper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static io.quarkus.test.extractor.project.utils.PluginUtils.TARGET_DIR;
import static java.nio.file.attribute.PosixFilePermission.*;

public final class FileSystemStorage {

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

    public static void saveFileContent(String fileName, String content) {
        saveFileContent(fileName, content, false);
    }

    public static void saveFileContent(String fileName, String content, boolean executable) {
        Path filePath = TARGET_DIR.resolve(fileName);
        try {
            Files.writeString(filePath, content, StandardOpenOption.CREATE_NEW);
            if (executable) {
                Files.setPosixFilePermissions(filePath, Set.of(GROUP_EXECUTE, OTHERS_EXECUTE, OWNER_EXECUTE, GROUP_READ,
                        OTHERS_READ, OWNER_READ));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save content to " + fileName, e);
        }
    }

    static void replaceFileContent(String fileName, String content) {
        Path filePath = TARGET_DIR.resolve(fileName);
        try {
            Files.writeString(filePath, content, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to replace file content: " + fileName, e);
        }
    }

    static void addToFile(String fileName, String content) {
        Path filePath = TARGET_DIR.resolve(fileName);
        if (!Files.exists(filePath)) {
            try {
                filePath.toFile().createNewFile();
            } catch (IOException e) {
                throw new RuntimeException("Failed to create file " + fileName, e);
            }
        }
        try {
            Files.writeString(filePath, content + System.lineSeparator(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException("Failed to append content to file " + fileName, e);
        }
    }

}
