package io.quarkus.test.extractor.utils;

import io.quarkus.test.extractor.project.result.ParentProject;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

public final class MavenUtils {

    public static final String POM_XML = "pom.xml";
    // used to avoid automatic substitution when we don't want it
    private static final String MAVEN_PROPERTY_PREFIX = "\\$USE-EXTRACTED-PROPERTIES\\{";
    private static final String PROPERTY_START = "\\${";
    private static final Set<String> IGNORED_PROPERTIES;

    static {
        // Maven properties we don't really need to propagate as they generate unnecessary noise
        // they exist because we use effective properties instead of properties from <properties> XML element
        // yes, we could avoid that entirely, but parsing Models would prolong execution time little
        // we can reconsider if there is any issue with current approach
        var properties = new HashSet<String>();
        properties.add("docker-prune.location");
        properties.add("revapi.buildFailureMessage");
        properties.add("project.build.outputTimestamp");
        properties.add("revapi.reportSeverity");
        properties.add("volume.access.modifier");
        properties.add("os.detected.name");
        properties.add("os.detected.arch");
        properties.add("os.detected.bitness");
        properties.add("os.detected.classifier");
        properties.add("os.detected.version");
        properties.add("os.detected.version.major");
        properties.add("os.detected.version.minor");
        properties.add("os.detected.release");
        properties.add("os.detected.release.version");
        properties.add("os.detected.release.like.fedora");
        IGNORED_PROPERTIES = Set.copyOf(properties);
    }

    private MavenUtils() {
        // utils
    }

    public static void writeMavenModel(Model model, Path targetDir) {
        writeMavenModel(model, getPomFile(targetDir));
    }

    public static void replacePomPlaceholders(Path targetDir) {
        replaceParentPomPlaceholders(getPomFile(targetDir));
    }

    public static boolean isTestModuleProperty(String propertyName, String propertyValue) {
        if (ParentProject.isPropertyDefinedInParentPom(propertyName, propertyValue)) {
            return false;
        }
        return isNotIgnoredProperty(propertyName);
    }

    public static boolean isNotIgnoredProperty(String propertyName) {
        return !IGNORED_PROPERTIES.contains(propertyName);
    }

    public static Model getMavenModel(String resourceName) {
        return getMavenModel(getResourceAsStream(resourceName));
    }

    private static Model getMavenModel(InputStream is) {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        try (is) {
            return reader.read(is);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Maven model", e);
        }
    }

    private static InputStream getResourceAsStream(String resourceName) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName);
    }

    private static File getPomFile(Path targetDir) {
        return targetDir.resolve(MavenUtils.POM_XML).toFile();
    }

    private static void writeMavenModel(Model model, File targetPom) {
        try (var newFileOS = new FileOutputStream(targetPom)) {
            new MavenXpp3Writer().write(newFileOS, model);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save '%s' POM file".formatted(targetPom), e);
        }
    }

    private static void replaceParentPomPlaceholders(File targetPom) {
        try {
            String pomContent = Files.readString(targetPom.toPath());
            pomContent = pomContent.replaceAll(MAVEN_PROPERTY_PREFIX, PROPERTY_START);
            Files.writeString(targetPom.toPath(), pomContent, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to remove Maven property placeholder from POM file", e);
        }
    }
}
