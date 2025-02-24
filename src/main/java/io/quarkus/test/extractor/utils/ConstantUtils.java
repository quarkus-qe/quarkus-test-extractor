package io.quarkus.test.extractor.utils;

import java.io.File;

public final class ConstantUtils {

    /**
     * Modules where we look for tests.
     */
    public static final String EXTENSIONS = "extensions";
    public static final String DEPLOYMENT = "deployment";
    public static final String INTEGRATION_TESTS = "integration-tests";
    /**
     * System property name that determines where to write generated projects.
     */
    public static final String WRITE_TO = "write-to";

    private ConstantUtils() {
    }

    public static boolean isExtensionDeploymentModule(String relativePath) {
        return relativePath.startsWith(EXTENSIONS + File.separator)
                && relativePath.endsWith(File.separator + DEPLOYMENT);
    }
}
