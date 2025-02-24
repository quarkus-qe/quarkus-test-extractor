package io.quarkus.test.extractor.utils;

import java.io.File;

public final class ConstantUtils {

    /**
     * Modules where we look for tests.
     */
    public static final String EXTENSIONS = "extensions";
    public static final String INTEGRATION_TESTS = "integration-tests";
    /**
     * System property name that determines where to write generated projects.
     */
    public static final String WRITE_TO = "write-to";
    private static final String DEPLOYMENT = "deployment";
    private static final String DEPLOYMENT_POSTFIX = "-" + DEPLOYMENT;
    private static final String DEPLOYMENT_NAME_POSTFIX = " - Deployment";

    private ConstantUtils() {
    }

    public static boolean isExtensionDeploymentModule(String relativePath) {
        return relativePath.startsWith(EXTENSIONS + File.separator)
                && relativePath.endsWith(File.separator + DEPLOYMENT);
    }

    public static String dropDeploymentPostfix(String text) {
        text = text.trim();
        if (text.endsWith(DEPLOYMENT_POSTFIX)) {
            return text.substring(0, text.length() - DEPLOYMENT_POSTFIX.length());
        }
        if (text.endsWith(DEPLOYMENT_NAME_POSTFIX)) {
            return text.substring(0, text.length() - DEPLOYMENT_NAME_POSTFIX.length()).trim();
        }
        return text;
    }
}
