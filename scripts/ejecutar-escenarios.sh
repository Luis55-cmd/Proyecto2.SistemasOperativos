#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
JAVA_BIN="${JAVA_HOME:?Define JAVA_HOME apuntando al JDK 25}/bin"
OUT=${1:-"$ROOT/escenarios/resultados"}
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)
"${MVN:-mvn}" -q -f "$ROOT/pom.xml" test-compile dependency:build-classpath -Dmdep.outputFile="$OUT/classpath.txt"
"$JAVA_BIN/java" -Djava.awt.headless=true -cp "$ROOT/target/classes:$ROOT/target/test-classes:$(cat "$OUT/classpath.txt")" \
    com.avilaos.gui.EscenariosJsonRunner "$ROOT/escenarios" "$OUT"
