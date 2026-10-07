#!/bin/sh
# Builds the Dad Coach backend jar for the lab from a private copy (a parallel `mvn` in the source tree is never disturbed).
# BACKEND_SRC defaults to this tree's backend/; point it at another worktree to run that branch.
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
SRC=${BACKEND_SRC:-$HERE/../backend}
COPY=${TMPDIR:-/tmp}/dc-lab-build
rsync -a --delete --exclude target "$SRC"/ "$COPY"/
(cd "$COPY" && JAVA_HOME=${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home} ./mvnw -q -DskipTests package)
JAR=$(ls -t "$COPY"/target/*.jar | grep -v -- '-plain' | head -1)
mkdir -p "$HERE/.run" && cp "$JAR" "$HERE/.run/backend.jar" && rm -rf "$COPY/target" && echo "built $(cd "$SRC" && git log --oneline -1)"
