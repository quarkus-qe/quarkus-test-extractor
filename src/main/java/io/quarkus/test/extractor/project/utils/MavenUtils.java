package io.quarkus.test.extractor.project.utils;

import io.quarkus.test.extractor.project.builder.Project;
import io.quarkus.test.extractor.project.helper.ExtractionSummary;
import io.quarkus.test.extractor.project.helper.FileChanger;
import io.quarkus.test.extractor.project.result.ParentProject;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;

import java.io.*;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static io.quarkus.test.extractor.project.helper.DisabledTest.isNotDisabledTest;
import static io.quarkus.test.extractor.project.result.ParentProject.isManagedByTestParent;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.stream.Collectors.joining;

public final class MavenUtils {

    public static final String QUARKUS_COMMUNITY_VERSION = "community.quarkus.version";
    public static final String QUARKUS_CORE_BOM_VERSION = "core.quarkus.version";
    public static final String QUARKUS_PLATFORM_VERSION = "quarkus.platform.version";
    public static final String QUARKUS_PLATFORM_GROUP_ID = "quarkus.platform.group-id";
    /**
     * Group id must not be io.quarkus as we need to keep modules artifact ids different from RHBQ bits we test.
     */
    public static final String TEST_PARENT_GROUP_ID = "io.quarkus.qe.tests";
    public static final String POM = "pom";
    public static final String POM_XML = "pom.xml";
    public static final String TEST_SCOPE = "test";
    public static final String COMPILE_SCOPE = "compile";
    public static final String QUARKUS_CORE_BOM_VERSION_REF = "${" + QUARKUS_CORE_BOM_VERSION + "}";
    public static final String QUARKUS_COMMUNITY_VERSION_REF = "${" + QUARKUS_COMMUNITY_VERSION + "}";
    public static final String USE_EXTRACTED_PROPERTIES = "USE-EXTRACTED-PROPERTIES";
    public static final String ANY = "*";
    public static final String JAR = "jar";    // like in ${project.version}
    public static final String THIS_PROJECT_VERSION = "project.version";
    public static final Set<String> COMMUNITY_DEPENDENCIES = Set.of("quarkus-grpc-protoc-plugin", "quarkus-extension-processor", "quarkus-test-grpc");
    // used to avoid automatic substitution when we don't want it
    private static final String MAVEN_PROPERTY_PREFIX = "\\$" + USE_EXTRACTED_PROPERTIES + "\\{";
    private static final String PROPERTY_START = "\\${";
    private static final Set<String> IGNORED_PROPERTIES;
    private static final String TEST_JAR = "test-jar";
    private static final String CENTRAL_REPOSITORY_ID = "central";
    private static final String GET_VERSION = "Version.getVersion()";

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
        finalizePom(targetDir, parentModule);
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

    private static void finalizePom(Path targetDir, boolean parentModule) {
        finalizePom(getPomFile(targetDir), parentModule);
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

    private static void finalizePom(File targetPom, boolean parentModule) {
        try {
            String pomContent = Files.readString(targetPom.toPath());
            pomContent = pomContent.replaceAll(MAVEN_PROPERTY_PREFIX, PROPERTY_START);
            if (!parentModule && pomContent.contains(THIS_PROJECT_VERSION)) {
                // this is "fallback" that exists mostly because plugin configurations doesn't have unified XML schema
                // that we could use, so when there is "${project.version}", we didn't detect that before
                var newPomLines = pomContent.lines().toArray(String[]::new);
                for (int i = 0; i < newPomLines.length; i++) {
                    String previousLine = i == 0 ? "" : newPomLines[i - 1];
                    String originalLine = newPomLines[i];
                    final String thisLine;
                    if (isIoQuarkusMavenPlugin(toDependency(originalLine, previousLine))) {
                        // this should be unnecessary, it is the last resort just in case replacement wasn't done sooner
                        thisLine = originalLine;
                        // this allows to use productized version of Quarkus Maven plugin
                        newPomLines[i - 1] = previousLine.replaceAll("io\\.quarkus", "\\${" + QUARKUS_PLATFORM_GROUP_ID + "}");
                    } else if (isManagedByTestParent(toDependency(previousLine))) {
                        // basically, if we manage this dependency, we want it to have our project version
                        thisLine = originalLine;
                    } else {
                        if (COMMUNITY_DEPENDENCIES.stream().anyMatch(previousLine::contains)) {
                            thisLine = originalLine.replaceAll(THIS_PROJECT_VERSION, QUARKUS_COMMUNITY_VERSION);
                        } else {
                            thisLine = originalLine.replaceAll(THIS_PROJECT_VERSION, QUARKUS_CORE_BOM_VERSION);
                        }
                    }
                    newPomLines[i] = thisLine;
                }
                pomContent = String.join(System.lineSeparator(), newPomLines);
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

    private static boolean isIoQuarkusMavenPlugin(Dependency dependency) {
        return dependency != null && PluginUtils.isIoQuarkusMavenPlugin(dependency.getArtifactId(), dependency.getGroupId());
    }

    private static Dependency toDependency(String line) {
        return toDependency(line, null);
    }

    private static Dependency toDependency(String line, String previousLine) {
        if (line == null || !line.contains("<artifactId>")) {
            return null;
        }
        line = line.trim();
        String restOfArtifactId = line.substring("<artifactId>".length());
        String artifactId = restOfArtifactId.substring(0, restOfArtifactId.indexOf("<"));
        var dep = new Dependency();
        dep.setArtifactId(artifactId);
        if (previousLine != null && previousLine.contains("<groupId>")) {
            previousLine = previousLine.trim();
            String restOfGroupId = previousLine.substring("<groupId>".length());
            String groupId = restOfGroupId.substring(0, restOfGroupId.indexOf("<"));
            dep.setGroupId(groupId);
        }
        return dep;
    }

    public static String getThisProjectVersion() {
        return "$" + USE_EXTRACTED_PROPERTIES + "{" + THIS_PROJECT_VERSION + "}";
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

    public static void setQuarkusCoreBomVersion(Dependency dependency) {
        dependency.setVersion("$" + USE_EXTRACTED_PROPERTIES + "{" + QUARKUS_CORE_BOM_VERSION + "}");
    }

    public static void setQuarkusCommunityVersion(Dependency dependency) {
        dependency.setVersion("$" + USE_EXTRACTED_PROPERTIES + "{" + QUARKUS_COMMUNITY_VERSION + "}");
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

    public static void correctVersionResolutionForForcedDeps(Path targetDir, ExtractionSummary extractionSummary) {
        // 'io.quarkus.builder.Version#getVersion' used in 'io.quarkus.test.QuarkusProdModeTest#setForcedDependencies'
        // and 'io.quarkus.test.QuarkusUnitTest#setForcedDependencies' provides incorrect values for RHBQ
        // because we need to use actual dependency version and not the platform BOM version
        // in most cases using core Quarkus BOM should do the trick, once you run into situation when it doesn't,
        // good luck fixing it
        final Consumer<Path> getVersionSubstitution = path -> FileChanger.changeContent(classContent -> {
            if (classContent.contains("quarkus-jdbc-h2")) {
                // TODO: drop this workaround when https://issues.redhat.com/browse/QUARKUS-6054 is fixed
                return classContent
                        .replaceAll(Pattern.quote(GET_VERSION), "System.getProperty(\"community.quarkus.version\")");
            }
            return classContent
                    .replaceAll(Pattern.quote(GET_VERSION), "System.getProperty(\"core.quarkus.version\")");
        }, path);
        try(var files = Files.walk(targetDir, Integer.MAX_VALUE, FileVisitOption.FOLLOW_LINKS)) {
            files
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith("Test.java") || p.toString().endsWith("TestCase.java"))
                .filter(anyLineMatches(l -> l.contains("import io.quarkus.test.QuarkusUnitTest;")
                        || l.contains("import io.quarkus.test.QuarkusProdModeTest;")))
                // this doesn't handle static 'version' method import, so far no-one had the great idea to do that
                // once they do, tests will start failing, and you can fix it...
                .filter(anyLineMatches(l -> l.contains("import io.quarkus.builder.Version;")))
                .filter(anyLineMatches(l -> l.contains("setForcedDependencies")))
                .peek(extractionSummary::addTestClassWithForcedDep)
                .map(p -> {
                    boolean ifKnownFormOfGettingVersionFound = anyLineMatches(l -> l.contains(GET_VERSION)).test(p);
                    if (ifKnownFormOfGettingVersionFound) {
                        return p;
                    }
                    throw new RuntimeException("""
                            Test class '%s' forces dependencies but does not contain string %s.
                            This can mean multiple things, like static method import was used, unused import, or the method
                            invocation is split among more than one lines, however you will need to look into
                            how the class looks like and implement substitution so that we can resolve the dependency
                            version based on artifacts we run this test with.
                            """.formatted(p, GET_VERSION));
                })
                .forEach(getVersionSubstitution);
        } catch (IOException e) {
            throw new RuntimeException("Failed to correct resolved version for forced dependencies", e);
        }
    }

    private static Predicate<Path> anyLineMatches(Predicate<String> predicate) {
        return p -> {
            try (var linesStream = Files.lines(p)) {
                return linesStream.anyMatch(predicate);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
