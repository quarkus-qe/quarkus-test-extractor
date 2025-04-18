package io.quarkus.test.extractor.project.utils;

import io.quarkus.test.extractor.project.builder.Project;
import io.quarkus.test.extractor.project.result.ParentProject;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.quarkus.test.extractor.project.helper.DisabledTest.isNotDisabledTest;
import static io.quarkus.test.extractor.project.result.ParentProject.isManagedByTestParent;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.stream.Collectors.joining;

public final class MavenUtils {

    /**
     * Group id must not be io.quarkus as we need to keep modules artifact ids different from RHBQ bits we test.
     */
    public static final String TEST_PARENT_GROUP_ID = "io.quarkus.qe.tests";
    public static final String POM = "pom";
    public static final String POM_XML = "pom.xml";
    public static final String TEST_SCOPE = "test";
    public static final String COMPILE_SCOPE = "compile";
    public static final String QUARKUS_PLATFORM_VERSION = "${quarkus.platform.version}";
    public static final String QUARKUS_COMMUNITY_VERSION = "${community.quarkus.version}";
    public static final String USE_EXTRACTED_PROPERTIES = "USE-EXTRACTED-PROPERTIES";
    public static final String ANY = "*";
    public static final String JAR = "jar";    // like in ${project.version}
    public static final String THIS_PROJECT_VERSION = "project.version";
    // used to avoid automatic substitution when we don't want it
    private static final String MAVEN_PROPERTY_PREFIX = "\\$" + USE_EXTRACTED_PROPERTIES + "\\{";
    private static final String PROPERTY_START = "\\${";
    private static final Set<String> IGNORED_PROPERTIES;
    private static final String TEST_JAR = "test-jar";
    private static final String CENTRAL_REPOSITORY_ID = "central";

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
        properties.add("jdk.min.version");
        properties.add("minimum-java-version");
        properties.add("native.surefire.skip");
        properties.add("impsort.skip");
        properties.add("gpg.skip");
        properties.add("maven.deploy.skip");
        properties.add("maven.compiler.argument.testTarget");
        properties.add("maven.compiler.release");
        properties.add("maven.compiler.source");
        properties.add("maven.compiler.testSource");
        properties.add("maven.compiler.argument.target");
        properties.add("maven.compiler.target");
        properties.add("maven.compiler.argument.testSource");
        properties.add("maven.compiler.argument.source");
        properties.add("maven.compiler.testTarget");
        properties.add("failsafe.argLine.additional");
        properties.add("develocity.pts.active");
        properties.add("revapi.newVersion");
        IGNORED_PROPERTIES = Set.copyOf(properties);
    }

    private MavenUtils() {
        // utils
    }

    public static void writeParentMavenModel(Model model, Path targetDir) {
        writeMavenModel(model, targetDir, true);
    }

    public static void writeMavenModel(Model model, Path targetDir) {
        writeMavenModel(model, targetDir, false);
    }

    private static void writeMavenModel(Model model, Path targetDir, boolean parentModule) {
        writeMavenModel(model, getPomFile(targetDir));
        replacePomPlaceholders(targetDir, parentModule);
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

    public static Model getMavenModel(Path pomXmlPath) {
        try {
            return getMavenModel(new FileInputStream(pomXmlPath.toFile()));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("POM file not found at " + pomXmlPath, e);
        }
    }

    public static Model getMavenModel(String resourceName) {
        return getMavenModel(getResourceAsStream(resourceName));
    }

    public static String loadResource(String resourceName) {
        try (var is = getResourceAsStream(resourceName)) {
            return new String(is.readAllBytes(), UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to resource " + resourceName, e);
        }
    }

    private static void replacePomPlaceholders(Path targetDir, boolean parentModule) {
        replacePomPlaceholders(getPomFile(targetDir), parentModule);
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

    public static File getPomFile(Path targetDir) {
        return targetDir.resolve(MavenUtils.POM_XML).toFile();
    }

    private static void writeMavenModel(Model model, File targetPom) {
        try (var newFileOS = new FileOutputStream(targetPom)) {
            new MavenXpp3Writer().write(newFileOS, model);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save '%s' POM file".formatted(targetPom), e);
        }
    }

    private static void replacePomPlaceholders(File targetPom, boolean parentModule) {
        try {
            String pomContent = Files.readString(targetPom.toPath());
            pomContent = pomContent.replaceAll(MAVEN_PROPERTY_PREFIX, PROPERTY_START);
            if (!parentModule && pomContent.contains(THIS_PROJECT_VERSION)) {
                // this is "fallback" that exists mostly because plugin configurations doesn't have unified XML schema
                // that we could use, so when there is "${project.version}", we didn't detect that before
                StringBuilder newPomContent = new StringBuilder();
                AtomicReference<String> previousLine = new AtomicReference<>("");
                pomContent.lines().forEachOrdered(line -> {
                    final String thisLine;
                    if (isManagedByTestParent(toDependency(previousLine.get()))) {
                        // basically, if we manage this dependency, we want it to have our project version
                        thisLine = line;
                    } else {
                        thisLine = line.replaceAll(THIS_PROJECT_VERSION, "quarkus.platform.version");
                    }
                    previousLine.set(thisLine);
                    newPomContent.append(thisLine).append(System.lineSeparator());
                });
                pomContent = newPomContent.toString();
            }
            if (!parentModule && pomContent.contains("docker-prune")) {
                // TODO: drop this block when https://github.com/quarkusio/quarkus/pull/47239 gets merged
                pomContent = pomContent
                        .lines()
                        .map(line -> {
                            final String thisLine;
                            if (line.trim().endsWith(".github/docker-prune.sh</executable>")) {
                                thisLine = "                                    <executable>${docker-prune.location}</executable>";
                            } else {
                                thisLine = line;
                            }
                            return thisLine;
                        })
                        .collect(joining(System.lineSeparator()));
            }
            Files.writeString(targetPom.toPath(), pomContent, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to remove Maven property placeholder from POM file", e);
        }
    }

    private static Dependency toDependency(String line) {
        if (line == null || !line.contains("<artifactId>")) {
            return null;
        }
        line = line.trim();
        String restOfArtifactId = line.substring("<artifactId>".length());
        String artifactId = restOfArtifactId.substring(0, restOfArtifactId.indexOf("<"));
        var dep = new Dependency();
        dep.setArtifactId(artifactId);
        return dep;
    }

    public static String getThisProjectVersion() {
        return "$USE-EXTRACTED-PROPERTIES{" + THIS_PROJECT_VERSION + "}";
    }

    public static String getManagementKey(Dependency dependency) {
        String fullManagementKey = dependency.getManagementKey();
        // drop 'jar' from io.quarkus:vertx-http:jar
        // why? some dependencies have like 'test-jar' type, and it confuses comparison,
        // but I don't think it means that they are not in fact managed
        return fullManagementKey.substring(0, fullManagementKey.lastIndexOf(':'));
    }

    public static boolean isTestJar(Dependency dependency) {
        return TEST_JAR.equalsIgnoreCase(dependency.getType());
    }

    public static boolean hasTestScope(Dependency dependency) {
        return TEST_SCOPE.equalsIgnoreCase(dependency.getType());
    }

    public static void setQuarkusPlatformVersion(Dependency dependency) {
        dependency.setVersion("$USE-EXTRACTED-PROPERTIES{quarkus.platform.version}");
    }

    public static void setQuarkusCommunityVersion(Dependency dependency) {
        dependency.setVersion("$USE-EXTRACTED-PROPERTIES{community.quarkus.version}");
    }

    public static String computeRelativePath(Project project) {
        int numberOfPathSeparators = (int) project.targetRelativePath().chars().filter(c -> c == File.separatorChar).count();
        return (".." + File.separator).repeat(numberOfPathSeparators + 1);
    }

    public static boolean isNotCentralRepository(Repository repository) {
        return !CENTRAL_REPOSITORY_ID.equalsIgnoreCase(repository.getId());
    }

    public static boolean isNotSurefireOrFailsafePlugin(String artifactId) {
        return !"maven-failsafe-plugin".equalsIgnoreCase(artifactId)
                && !"maven-surefire-plugin".equalsIgnoreCase(artifactId);
    }

    public static boolean hasJarPackaging(Project project) {
        return project.packagingType() == null || JAR.equalsIgnoreCase(project.packagingType());
    }

    public static boolean hasThisProjectVersion(Dependency dependency) {
        return dependency.getVersion() != null && dependency.getVersion().contains(THIS_PROJECT_VERSION);
    }

    public static boolean isPomPackageType(Dependency dep) {
        return POM.equalsIgnoreCase(dep.getType());
    }

    public static void copyDirectory(File sourceDirectory, File destinationDirectory, boolean containsDisabledTests,
                                     String artifactId) {
        destinationDirectory.mkdirs();
        copyDirectory(sourceDirectory, destinationDirectory, destinationDirectory, containsDisabledTests, artifactId);
    }

    private static void copyDirectory(File sourceDir, File destinationDir, File originalDir,
                                      boolean containsDisabledTests, String artifactId) {
        File[] files = sourceDir.listFiles();
        if (files != null) {
            String sourcePath = sourceDir.getAbsolutePath();
            for(File source : files) {
                if (!source.equals(originalDir)) {
                    String dest = source.getAbsolutePath();
                    dest = dest.substring(sourcePath.length() + 1);
                    File destination = new File(destinationDir, dest);
                    if (source.isFile()) {
                        if (!containsDisabledTests || isNotDisabledTest(artifactId, source)) {
                            destination = destination.getParentFile();
                            File destinationFile = new File(destination, source.getName());
                            if (!destinationFile.exists()) {
                                try {
                                    destinationFile.createNewFile();
                                } catch (IOException e) {
                                    throw new RuntimeException("Failed to create file " + destinationFile, e);
                                }
                            }
                            try {
                                Files.copy(source.toPath(), destinationFile.toPath(), REPLACE_EXISTING);
                            } catch (IOException e) {
                                throw new RuntimeException("Failed to copy '%s' file to '%s'"
                                        .formatted(source.getPath(), destinationFile.getPath()), e);
                            }
                        }
                    } else {
                        destination.mkdirs();
                        copyDirectory(source, destination, originalDir, containsDisabledTests, artifactId);
                    }
                }
            }

        }
    }

    public static String getProfilePostfix(Project project) {
        // VT only tests should only run with Java 21
        return project.targetRelativePath().contains("integration-tests/virtual-threads") ? "-21" : "";
    }

    public static Optional<Profile> getProfile(Model model, String x) {
        return model
                .getProfiles()
                .stream()
                .filter(p -> x.equalsIgnoreCase(p.getId()))
                .findFirst();
    }
}
