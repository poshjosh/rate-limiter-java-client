#!/usr/bin/env bash

set -euo pipefail

MVN_SETTINGS_FILE=${MVN_SETTINGS_FILE:-~/dev_looseboxes/.m2/settings.xml}
DEBUG=${DEBUG:-false}
GPG_PASS=${GPG_PASS:-}

cd .. || exit 1

export JAVA_HOME=~/.sdkman/candidates/java/11

if [ -f ".env" ]; then
    echo "Exporting environment"

    set -a
    source .env
    set +a
fi

if [ "${DEBUG}" = "true" ] || [ "$DEBUG" = true ]; then
    DEBUG="-X -e"
    echo "MVN_SETTINGS_FILE=$MVN_SETTINGS_FILE"
    echo "DEBUG=$DEBUG"
    echo "JAVA_HOME=$JAVA_HOME"
else
    DEBUG=
fi

# shellcheck disable=SC2086
# We disable the need for double quotes here, as using double quotes caused errors.
# TODO - Tests are not running - Fix it
if [ -z ${GPG_PASS+x} ] || [ "$GPG_PASS" = "" ]; then
    mvn -s "$MVN_SETTINGS_FILE" clean verify $DEBUG -Dspring.profiles.active=test
    echo "Build SUCCESSFUL"
else
    mvn -s "$MVN_SETTINGS_FILE" clean deploy $DEBUG -P release -Dgpg.passphrase=$GPG_PASS
    echo "Release SUCCESSFUL"
    echo "Please browse to https://s01.oss.sonatype.org/#stagingRepositories and manually confirm the release."
fi

