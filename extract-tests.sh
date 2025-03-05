#!/bin/bash

QUARKUS_URL='https://github.com/quarkusio/quarkus.git'
QUARKUS_GIT_CHECKOUT='main'
QUARKUS_SOURCE_DIR=$PWD
VERBOSE=false
TARGET_DIR='/tmp/extracted-tests'
EXTRACTED_TESTS_PROJECT='quarkus-qe/quarkus-extracted-tests'
PUSH_EXTRACTED_TESTS=false
WORKING_DIR='/tmp/test-extractor'
SUPER_VERBOSE=false
SKIP_QUARKUS_BUILD=false
GH_TOKEN=''

while getopts l:b:d:vt:u:n:pxw:sg: opt
do
    case "${opt}" in
        l) QUARKUS_URL=${OPTARG};;
        b) QUARKUS_GIT_CHECKOUT=${OPTARG};;
        d) QUARKUS_SOURCE_DIR=${OPTARG};;
        v) VERBOSE=true;;
        t) TARGET_DIR=${OPTARG};;
        u) EXTRACTED_TESTS_PROJECT=${OPTARG};;
        n) EXTRACTED_TESTS_PROJECT_BRANCH=${OPTARG};;
        p) PUSH_EXTRACTED_TESTS=true;;
        w) WORKING_DIR=${OPTARG};;
        x) SUPER_VERBOSE=true;;
        s) SKIP_QUARKUS_BUILD=true;;
        g) GH_TOKEN=${OPTARG};;
    esac
done

# enforce options defaults
if [ "${EXTRACTED_TESTS_PROJECT_BRANCH+set}" != set ]; then
  EXTRACTED_TESTS_PROJECT_BRANCH="${QUARKUS_GIT_CHECKOUT}"
fi
if [ "$SUPER_VERBOSE" = true ]; then
  VERBOSE=true
fi

# print out commands for better understanding of what is happening
if [ "$VERBOSE" = true ]; then
  set -x
fi

# inform about script configuration
if [ "$VERBOSE" = true ]; then
    echo 'Running in a verbose mode with following configuration:'
    echo '- Quarkus project URL:' $QUARKUS_URL
    echo '- Quarkus project branch or tag:' $QUARKUS_GIT_CHECKOUT
    echo '- Quarkus project source directory:' $QUARKUS_SOURCE_DIR
    echo '- Test extraction result will be placed in' $TARGET_DIR
    if [ "$PUSH_EXTRACTED_TESTS" = true ] ; then
      echo '- Extracted tests will be pushed to the project' $EXTRACTED_TESTS_PROJECT 'and branch' $EXTRACTED_TESTS_PROJECT_BRANCH
    fi
fi

# if the extractor plugin is not available, make it available
PREVIOUS_DIR=$PWD
mvn io.quarkus.qe:quarkus-test-extractor:1.0-SNAPSHOT:help > /dev/null
exitCode=$?
if [ $exitCode -ne 0 ]; then
    mkdir -p $WORKING_DIR
    cd $WORKING_DIR
    git clone https://github.com/quarkus-qe/quarkus-test-extractor.git --depth=1
    cd quarkus-test-extractor
    mvn clean install -DskipTests -DskipITs
    cd $PREVIOUS_DIR
fi

# if this script is not executed in Quarkus source directory, go there
if ! test $QUARKUS_SOURCE_DIR -ef $PWD; then
  mkdir -p $QUARKUS_SOURCE_DIR
fi
cd $QUARKUS_SOURCE_DIR

# if Quarkus source is not available, get it
if ! test -f './pom.xml'; then
  git clone $QUARKUS_URL --depth=1
  cd quarkus
  git fetch --tags
  git checkout -B $QUARKUS_GIT_CHECKOUT $QUARKUS_GIT_CHECKOUT
else
  if [ $QUARKUS_GIT_CHECKOUT != 'main' ]; then
    QUARKUS_GIT_CHECKOUT="$(git rev-parse --abbrev-ref HEAD)"
  else
    git fetch --tags
    git checkout -B $QUARKUS_GIT_CHECKOUT $QUARKUS_GIT_CHECKOUT
  fi
  if [ "$VERBOSE" = true ]; then
    echo 'Detected POM file and git tag or branch' $QUARKUS_GIT_CHECKOUT ', assuming Quarkus project already exists'
  fi
fi

# save git HEAD
QUARKUS_GIT_HEAD=$(git rev-parse --short HEAD)

# recreate directory with extraction results
rm $TARGET_DIR -r -f || true > /dev/null
mkdir -p $TARGET_DIR

# use auxiliary file to filter how much information is logged
if test -f test-extraction-log; then
  rm test-extraction-log
fi
touch test-extraction-log

# enable debug in the Maven
ADDITIONAL_ARGS=''
if [ "$SUPER_VERBOSE" = true ]; then
  ADDITIONAL_ARGS='-X'
fi

# if we also want to push the extracted tests, we need to prepare git project
if [ "$PUSH_EXTRACTED_TESTS" = true ]; then
  echo 'Preparing' $EXTRACTED_TESTS_PROJECT 'project'
  PREVIOUS_DIR=$PWD
  cd $TARGET_DIR/..
  # get target dir name (remainder after the last path separator)
  TARGET_DIR_NAME=${TARGET_DIR##*/ }
  gh repo clone $EXTRACTED_TESTS_PROJECT $TARGET_DIR_NAME -- --depth=1
  cd $TARGET_DIR_NAME
  git checkout -B $EXTRACTED_TESTS_PROJECT_BRANCH
  if [ -n "$(ls -A . 2>/dev/null)" ]
  then
    # remove all files as we will provide a new version (we will extract new tests)
    git rm -r *
  fi
  cd $PREVIOUS_DIR
fi

# build Quarkus because some artifacts are not available in Maven central
if [ "$SKIP_QUARKUS_BUILD" = false ]; then
  echo 'Building Quarkus'
  MAVEN_OPTS="-Xmx4g" ./mvnw -B --no-transfer-progress -Dquickly
else
  echo 'Skipping Quarkus build'
fi

# collect metadata about Quarkus BOM
echo 'Collecting metadata about Quarkus BOM'
mvn -f bom/application/ io.quarkus.qe:quarkus-test-extractor:1.0-SNAPSHOT:parse-quarkus-bom -Dwrite-to=$TARGET_DIR $ADDITIONAL_ARGS >> test-extraction-log

# extract tests
echo 'Extracting tests from Quarkus ' $QUARKUS_GIT_HEAD ', you will be informed about the extraction result'
mvn io.quarkus.qe:quarkus-test-extractor:1.0-SNAPSHOT:extract-tests -Dwrite-to=$TARGET_DIR $ADDITIONAL_ARGS >> test-extraction-log

# detect whether the extraction succeeded
exitCode=$?
if [ $exitCode -ne 0 ]; then
    echo 'Failed to extract tests, if the extraction did not fail for obvious reason, enable super verbose mode with x option. Extraction logs:'
    cat test-extraction-log
    exit 1
elif [ "$VERBOSE" = true ]; then
  echo 'Test extraction succeeded, extraction logs: '
  cat test-extraction-log
else
  echo 'Test extraction succeeded'
fi

# go to directory with extracted tests
cd $TARGET_DIR

# inform about extraction details
if [ "$VERBOSE" = true ]; then
  cat extraction-summary
fi

# delete auxiliary files and directories, but keep 'extraction-summary' as it might be useful
rm -r -f quarkus-bom-managed-deps quarkus-build-parent-context quarkus-parent-pom-context partial-extraction-summaries

# push extracted tests to dedicated GitHub project
if [ "$PUSH_EXTRACTED_TESTS" = true ]; then
  PUSH_REMOTE='origin'
  if [[ -n ${GH_TOKEN} ]]; then
    echo "Detected GitHub token, setting up git user config"
    gh auth setup-git
    git config --local user.email "quarkus-qe@redhat.com"
    git config --local user.name "QuarkusQE"
    git remote add qe-fork https://github.com/QuarkusQE/quarkus-extracted-tests.git
    PUSH_REMOTE='qe-fork'
  fi

  mkdir -p .github/workflows
  wget -O .github/workflows/branches-pr.yaml --retry-connrefused --waitretry=1 --read-timeout=20 --timeout=15 -t 10 https://raw.githubusercontent.com/quarkus-qe/quarkus-extracted-tests/refs/heads/main/.github/workflows/branches-pr.yaml

  # some tests contains so called "secrets" and it's impossible to convince GH they are no secrets
  wget -O .github/secret_scanning.yml --retry-connrefused --waitretry=1 --read-timeout=20 --timeout=15 -t 10 https://raw.githubusercontent.com/quarkus-qe/quarkus-extracted-tests/refs/heads/main/.github/secret_scanning.yml

  echo "Tests extracted for Quarkus $QUARKUS_GIT_HEAD" > README.MD
  git add *
  GIT_COMMIT_OUTPUT=$(git commit -am "Add tests extracted from $QUARKUS_GIT_HEAD")
  if [[ $GIT_COMMIT_OUTPUT == *"nothing to commit"* ]]; then
    echo 'Nothing to push as there were no relevant changes'
    exit 0
  fi
  if [ "$VERBOSE" = true ]; then
    git status
  fi
  PR_BRANCH="temporary-branch/pr/$QUARKUS_GIT_CHECKOUT"
  git checkout -b $PR_BRANCH
  git push $PUSH_REMOTE $PR_BRANCH -f
  echo 'Opening PR in project' $EXTRACTED_TESTS_PROJECT
  PR_MESSAGE="Adding tests extracted from $QUARKUS_URL $QUARKUS_GIT_CHECKOUT with HEAD on $QUARKUS_GIT_HEAD"
  PR_TITLE="Add new tests extracted from $QUARKUS_GIT_CHECKOUT tag"
  gh pr create -r 'QuarkusQE' -b "$PR_MESSAGE" -t "$PR_TITLE" -B "$EXTRACTED_TESTS_PROJECT_BRANCH" -R "$EXTRACTED_TESTS_PROJECT" -H $PR_BRANCH
fi
