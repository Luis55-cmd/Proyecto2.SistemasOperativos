#!/usr/bin/env bash
# Compila copias temporales; nunca muta el checkout ni usa sus target/classes.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
JAVA_BIN="${JAVA_HOME:?Define JAVA_HOME apuntando al JDK 25}/bin"
TMP=$(mktemp -d "${TMPDIR:-/tmp}/avilaos-mutaciones.XXXXXX")
trap 'rm -rf "$TMP"' EXIT
OUT=${1:-$(mktemp -d "${TMPDIR:-/tmp}/avilaos-evidencia.XXXXXX")}
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)
CLASS=com.avilaos.core.scheduler.PriorityDispatchTest
# Resolver las dependencias del pom también para las compilaciones aisladas.
"${MVN:-mvn}" -q -f "$ROOT/pom.xml" dependency:build-classpath -Dmdep.outputFile="$TMP/classpath.txt"
DEPS=$(cat "$TMP/classpath.txt")
compilar() {
    local dir=$1
    mkdir -p "$dir/classes"
    rg --files "$dir/src" -g '*.java' | while IFS= read -r source; do
        printf '"%s"\n' "$source"
    done > "$dir/sources.txt"
    "$JAVA_BIN/javac" -cp "$DEPS" -d "$dir/classes" "@$dir/sources.txt" > "$dir/compilacion.txt" 2>&1 || {
        cat "$dir/compilacion.txt"; return 1;
    }
}
mkdir -p "$TMP/base"
cp -R "$ROOT/src" "$TMP/base/src"
compilar "$TMP/base"
"$JAVA_BIN/java" -Djava.awt.headless=true -cp "$TMP/base/classes:$DEPS" "$CLASS" | tee "$OUT/base.txt"
mutacion() {
    local caso=$1 archivo=$2 original=$3 cambio=$4 mensaje=$5
    local testclass=${6:-$CLASS}
    local dir="$TMP/$caso"
    mkdir -p "$dir"
    cp -R "$ROOT/src" "$dir/src"
    ORIGINAL="$original" CAMBIO="$cambio" perl -0777 -i -pe '
        $n = s/\Q$ENV{ORIGINAL}\E/$ENV{CAMBIO}/g;
        die "Se esperaba exactamente una coincidencia, hubo $n\n" unless $n == 1;
    ' "$dir/src/main/java/com/avilaos/$archivo"
    compilar "$dir"
    local status=0
    "$JAVA_BIN/java" -Djava.awt.headless=true -cp "$dir/classes:$DEPS" "$testclass" "$caso" > "$dir/ejecucion.txt" 2>&1 || status=$?
    {
        printf 'Caso: %s\nArchivo: %s\nOriginal: %s\nMutacion: %s\nCompilacion: PASS\nExit: %s\n' "$caso" "$archivo" "$original" "$cambio" "$status"
        cat "$dir/ejecucion.txt"
    } > "$OUT/$caso.txt"
    if [[ $status -eq 0 ]] || ! rg -Fq "AssertionError: $mensaje" "$dir/ejecucion.txt"; then
        cat "$OUT/$caso.txt"
        printf 'INVALIDA: %s\n' "$caso"; return 1
    fi
    printf 'KILLED %s\n' "$caso"
}
mutacion inicial core/scheduler/PreemptivePriorityScheduler.java '        sortQueue(targetQueue);

        // Si hay un proceso' '        // Si hay un proceso' 'INICIAL: seleccionar Alta'
mutacion apropiacion core/scheduler/PreemptivePriorityScheduler.java 'if (isStrictlyHigherPriority(candidate, currentRunning))' 'if (false)' 'APROPIACION: candidato oculto'
printf 'Evidencia: %s\n' "$OUT"
