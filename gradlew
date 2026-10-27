#!/bin/sh
# Gradle startup script for POSIX shells
APP_HOME=$(cd "$(dirname "$0")" && pwd -P) || exit
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
if [ -n "$JAVA_HOME" ]; then
    JAVACMD=$JAVA_HOME/bin/java
else
    JAVACMD=java
fi
exec "$JAVACMD" -Xmx64m -Xms64m $JAVA_OPTS $GRADLE_OPTS "-Dorg.gradle.appname=gradlew" \
    -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
