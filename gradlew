#!/bin/sh
#
# Copyright © 2015-2021 the original authors.
# Licensed under the Apache License, Version 2.0

##############################################################################
# Gradle start up script for UN*X
##############################################################################

die() {
    echo
    echo "ERROR: $*"
    echo
    exit 1
}

# Resolve APP_HOME
app_path=$0
while [ -h "$app_path" ] ; do
    ls_out=$(ls -ld "$app_path")
    link=$(expr "$ls_out" : '.*-> \(.*\)$')
    if expr "$link" : '/.*' > /dev/null; then
        app_path="$link"
    else
        app_path=$(dirname "$app_path")"/$link"
    fi
done
APP_HOME=$(cd "$(dirname "$app_path")" && pwd)

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Determine Java command
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
    [ -x "$JAVACMD" ] || die "JAVA_HOME points to an invalid Java installation: $JAVA_HOME"
else
    JAVACMD=java
    command -v java >/dev/null 2>&1 || die "JAVA_HOME is not set and 'java' was not found in PATH."
fi

DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Build arguments
APP_ARGS="$*"

# Execute
eval exec '"$JAVACMD"' $DEFAULT_JVM_OPTS '"$JAVA_OPTS"' '"$GRADLE_OPTS"' \
    '"-Dorg.gradle.appname=$APP_BASE_NAME"' \
    -classpath '"$CLASSPATH"' \
    org.gradle.wrapper.GradleWrapperMain \
    '"$APP_ARGS"'
