# Quarkus Test Extractor

This is Maven plugin that extracts tests from the https://github.com/quarkusio/quarkus project so that they can be run independently with any compatible Quarkus platform artifacts.
The plugin enables you to run Quarkus main project tests with delivered Quarkus artifacts, without source code.
It is useful if you need to verify that your build of Quarkus works as expected.

# Prerequisites

Installed Git and Maven 3.9.9 or newer 3.X version. Only supported OS is Fedora / RHEL.
If you plan to push extracted tests to the Quarkus QE Extracted Tests project:
- valid GitHub token must be available as `GH_TOKEN` environment variable
- GitHub repository must be set as `GH_REPO` environment variable
- GH CLI must be installed

# Use the `extract-tests.sh` script to extract tests

```bash
wget -q https://raw.githubusercontent.com/quarkus-qe/quarkus-test-extractor/refs/heads/main/extract-tests.sh
chmod +x extract-tests.sh
./extract-tests.sh
```

## What the `extract-tests.sh` script can do

* Clone this project (skipped when the plugin artifacts are detected)
* Build this project (skipped when the plugin artifacts are detected)
* Clone Quarkus main project (skipped when Quarkus project directory contains a POM file)
* Go to Quarkus main project (skipped when Quarkus project directory contains a POM file)
* Checkout specified Quarkus tag or branch (skipped when Quarkus project directory contains a POM file)
* Build Quarkus (because some artifacts are not available in Maven central)
* Extract tests from extensions deployment modules and integration test modules
* Summarize extraction, e.g. what dependency versions are hardcoded because they are not managed by Quarkus BOM
* If explicitly enabled, push tests to Quarkus QE Extracted Tests project:
  * if the GitHub token is provided, configure git user
  * clone the target project
  * checkout specified branch (create it if not exist)
  * delete all existing files in the project and replace them with newly extracted tests
  * download and add new PR workflow for the branch with extracted tests
  * commit and push all changes to the Quarkus QE Extracted Tests project
  * open PR to the Quarkus QE Extracted Tests project

## Available script options

The `extract-tests.sh` script accepts following options:

| Option | Supported values        | Description                                                       | Default value                                               |
|--------|-------------------------|-------------------------------------------------------------------|-------------------------------------------------------------|
| -l     | HTTPS git link          | Quarkus project URL                                               | `https://github.com/quarkusio/quarkus.git`                  |
| -b     | existing branch or tag  | Quarkus project tag or branch                                     | current branch if project already existed, `main` otherwise |
| -d     | path to directory       | Quarkus project directory                                         | `.`                                                         |
| -v     | no values               | Log all messages like Quarkus build                               | silent unless super verbose mode is enabled with `x`        |
| -x     | no values               | Enable Maven plugin goal execution debugging with `-X`            | silent                                                      |
| -t     | path to directory       | Where to put extracted tests                                      | `/tmp/extracted-tests`                                      |
| -u     | OWNER / REPO syntax     | Quarkus QE Extracted Tests project                                | `quarkus-qe/quarkus-extracted-tests`                        |
| -n     | branch (existing / new) | Quarkus QE Extracted Tests target branch                          | value of the `-b` option                                    |
| -p     | no values               | If to push extracted tests to the project                         | not pushed                                                  |
| -w     | path to directory       | Working directory (e.g. where this plugin is built)               | `/tmp/test-extractor`                                       |
| -s     | no values               | Skip Quarkus build if you are certain all artifacts are available | Quarkus is built everytime                                  |
| -g     | GitHub token            | Token used to authenticate with GitHub CLI and setup git user     | no value                                                    |

