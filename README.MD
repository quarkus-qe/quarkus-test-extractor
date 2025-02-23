# Quarkus Test Extractor

This is Maven plugin that extracts tests from the https://github.com/quarkusio/quarkus project so that they can be run independently with any compatible Quarkus platform artifacts.
The plugin enables you to run Quarkus main project tests with delivered Quarkus artifacts, without source code.
It is useful if you need to verify that your build of Quarkus works as expected.

# Prerequisites

Installed Maven 3.9.9 or newer 3.X version.

# Build this plugin

Run following commands:

```bash
git clone git@github.com:quarkus-qe/quarkus-test-extractor.git
cd quarkus-test-extractor
mvn clean install
```

# How to use this plugin

Run following commands (use any branch):

```bash
git clone git@github.com:quarkusio/quarkus.git -b main
cd quarkus
mvn io.quarkus.qe:quarkus-test-extractor:1.0-SNAPSHOT:extract-tests -Dwrite-to=/tmp/test-1
```

And find your tests in the `/tmp/test-1` directory.