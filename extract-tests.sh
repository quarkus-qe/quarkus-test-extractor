# FIXME: take Quarkus branch as argument
# FIXME: no branch was specified, assume main and log about it
# FIXME: if already in Quarkus project, all is good
# FIXME: if not in Quarkus project, clone it here
# FIXME: allow to specify working dir, default is '.'
# FIXME: make extracting tests silent but validate how it went
# FIXME: print logs in a verbose mode
# FIXME: store logs as job artifacts
# FIXME: store extraction summary as artifact and print it out after execution
# FIXME: do something with extracted tests, e.g. push them to configured repo (and allow to configure repo)
# FIXME: probably open PR and auto-merge it when CI is green, if CI is red, send an e-mail
# FIXME: ideally this script would be triggered by a release or manually
# FIXME: document how this script is triggered
# FIXME: delete previous extracted tests target if exists

# FIXME: test for Maven version

# FIXME: do this conditionally when not available in local repository
git clone git@github.com:quarkus-qe/quarkus-test-extractor.git
cd quarkus-test-extractor
mvn clean install

# FIXME: this is just example, but needs to be elsewhere and conditional
mvn -f bom/application/ io.quarkus.qe:quarkus-test-extractor:1.0-SNAPSHOT:parse-quarkus-bom -Dwrite-to=/tmp/a
mvn io.quarkus.qe:quarkus-test-extractor:1.0-SNAPSHOT:extract-tests -Dwrite-to=/tmp/a