QUARKUS_URL='git@github.com:quarkusio/quarkus.git'
QUARKUS_BRANCH='main'
QUARKUS_SOURCE_DIR=$PWD
VERBOSE=false
TARGET_DIR='/tmp/extracted-tests'
EXTRACTED_TESTS_PROJECT_URL='git@github.com:quarkus-qe/quarkus-extracted-tests.git'
PUSH_EXTRACTED_TESTS=false
WORKING_DIR='/tmp/test-extractor'
SUPER_VERBOSE=false

while getopts l:b:d:vt:u:n:pxw: opt
do
    case "${opt}" in
        l) QUARKUS_URL=${OPTARG};;
        b) QUARKUS_BRANCH=${OPTARG};;
        d) QUARKUS_SOURCE_DIR=${OPTARG};;
        v) VERBOSE=true;;
        t) TARGET_DIR=${OPTARG};;
        u) EXTRACTED_TESTS_PROJECT_URL=${OPTARG};;
        n) EXTRACTED_TESTS_PROJECT_BRANCH=${OPTARG};;
        p) PUSH_EXTRACTED_TESTS=true;;
        w) WORKING_DIR=${OPTARG};;
        x) SUPER_VERBOSE=true;;
    esac
done

# enforce options defaults
if [ "${EXTRACTED_TESTS_PROJECT_BRANCH+set}" != set ]; then
  EXTRACTED_TESTS_PROJECT_BRANCH="${QUARKUS_BRANCH}"
fi
if [ "$SUPER_VERBOSE" = true ]; then
  VERBOSE=true
fi

# inform about script configuration
if [ "$VERBOSE" = true ]; then
    echo 'Running in a verbose mode with following configuration:'
    echo '- Quarkus project URL:' $QUARKUS_URL
    echo '- Quarkus project branch:' $QUARKUS_BRANCH
    echo '- Quarkus project source directory:' $QUARKUS_SOURCE_DIR
    echo '- Test extraction result will be placed in' $TARGET_DIR
    if [ "$PUSH_EXTRACTED_TESTS" = true ] ; then
      echo '- Extracted tests will be pushed to the project with URL' $EXTRACTED_TESTS_PROJECT_URL 'and branch' $EXTRACTED_TESTS_PROJECT_BRANCH
    fi
fi

# if the extractor plugin is not available, make it available
PREVIOUS_DIR=$PWD
mvn io.quarkus.qe:quarkus-test-extractor:1.0-SNAPSHOT:help > /dev/null
exitCode=$?
if [ $exitCode -ne 0 ]; then
    mkdir -p $WORKING_DIR
    cd $WORKING_DIR
    git clone git@github.com:quarkus-qe/quarkus-test-extractor.git
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
  git clone $QUARKUS_URL -b $QUARKUS_BRANCH
  cd quarkus
elif [ "$VERBOSE" = true ]; then
  echo 'Detected POM file, assuming Quarkus project already exists'
fi

# recreate directory with extraction results
rm $TARGET_DIR -r || true > /dev/null
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

# collect metadata about Quarkus BOM
echo 'Collecting metadata about Quarkus BOM'
mvn -f bom/application/ io.quarkus.qe:quarkus-test-extractor:1.0-SNAPSHOT:parse-quarkus-bom -Dwrite-to=$TARGET_DIR $ADDITIONAL_ARGS >> test-extraction-log

# extract tests
echo 'Extracting tests, you will be informed about the extraction result'
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
cat extraction-summary

# delete auxiliary files and directories
rm -r extraction-summary quarkus-bom-managed-deps quarkus-build-parent-context quarkus-parent-pom-context partial-extraction-summaries

# push extracted tests to dedicated GitHub project
if [ "$PUSH_EXTRACTED_TESTS" = true ]; then
  echo 'Pushing extracted tests to' $EXTRACTED_TESTS_PROJECT_URL 'branch' $EXTRACTED_TESTS_PROJECT_BRANCH
  # FIXME: before extraction begins, we need to check out the project, the branch and delete content
  #   now we need to just add all things to git and open PR
fi

# FIXME: store logs as job artifacts
# FIXME: store extraction summary as artifact and print it out after execution
# FIXME: do something with extracted tests, e.g. push them to configured repo (and allow to configure repo)
# FIXME: probably open PR and auto-merge it when CI is green, if CI is red, send an e-mail
# FIXME: ideally this script would be triggered by a release or manually
# FIXME: document how this script is triggered
