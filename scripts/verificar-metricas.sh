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
CLASS=com.avilaos.gui.MetricasTest
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
METRICAS=com.avilaos.gui.MetricasTest
MONITOR=com.avilaos.gui.MonitorSimulacionTest
"$JAVA_BIN/java" -Djava.awt.headless=true -cp "$TMP/base/classes:$DEPS" "$METRICAS" > "$OUT/metricas-base.txt"
"$JAVA_BIN/java" -Djava.awt.headless=true -cp "$TMP/base/classes:$DEPS" "$MONITOR" > "$OUT/monitor-base.txt"
mutacion pestana gui/MonitorSimulacion.java 'pestanas.addTab("Métricas", panelMetricas);
            if (viendoMetricas)' 'if (viendoMetricas)' 'Vacío' "$MONITOR"
mutacion llegada simulation/SimulationController.java 'pcb.setArrivalCycle(getCurrentCycle());' 'pcb.setArrivalCycle(0);' 'LLEGADA:' "$METRICAS"
mutacion actividad core/kernel/ComputerKernel.java 'comp.incrementarCicloActivo();' '// omitir actividad' 'ACTIVIDAD:' "$METRICAS"
mutacion observado core/kernel/ComputerKernel.java 'comp.registrarCicloObservado();' '// omitir observado' 'ACTIVIDAD:' "$METRICAS"
mutacion final hardware/Computer.java 'procesosCompletados++;' '// omitir finalización' 'FINAL:' "$METRICAS"
mutacion respuesta hardware/Computer.java 'pcb.getStartCycle() - pcb.getArrivalCycle()' 'cicloActual - pcb.getArrivalCycle()' 'RESPUESTA:' "$METRICAS"
mutacion aborto hardware/Computer.java 'if (!completo) return;' 'if (false) return;' 'ABORTO:' "$METRICAS"
mutacion utilizacion gui/PanelMetricas.java 'c.getCiclosActivos() / c.getCiclosObservados()' 'c.getCiclosActivos() / cicloActual' 'UTILIZACION:' "$METRICAS"
mutacion eje gui/PanelMetricas.java 'addOrUpdate((double) cicloActual, utilizacion)' 'addOrUpdate((double) cicloActual + 1, utilizacion)' 'EJE:' "$METRICAS"
mutacion duplicados gui/PanelMetricas.java 'new XYSeries(actuales[i].getName(), true, false)' 'new XYSeries(actuales[i].getName(), true, true)' 'GRAFICO:' "$METRICAS"
mutacion historia gui/PanelMetricas.java 'if (nodos[j] == actuales[i])' 'if (false)' 'HISTORIA:' "$METRICAS"
mutacion menu gui/PanelPrincipal.java 'boton.addActionListener(e -> mostrarPanel(destino));' 'boton.addActionListener(e -> mostrarPanel(destino == Destino.METRICAS ? Destino.MENU : destino));' 'MENU:' "$MONITOR"
"$JAVA_BIN/java" -Djava.awt.headless=true -cp "$TMP/base/classes:$DEPS" com.avilaos.memory.BufferMemoryTest > "$OUT/buffer-base.txt"
mutacion buffer_ram simulation/SimulationController.java 'storage, id, new MyQueue<>(), storage);' 'storage, id, new MyQueue<>(), memory);' 'CAPACIDAD:' com.avilaos.memory.BufferMemoryTest
"$JAVA_BIN/java" -Djava.awt.headless=true -cp "$TMP/base/classes:$DEPS" com.avilaos.gui.BufferMemoryGuiTest > "$OUT/buffer-gui-base.txt"
mutacion buffer_editable gui/PanelPrincipal.java 'memoriaBuffer.setEditable(false);' 'memoriaBuffer.setEditable(true);' 'LECTURA:' com.avilaos.gui.BufferMemoryGuiTest
mutacion buffer_preview gui/PanelPrincipal.java 'memoriaBuffer.setText(spinnerAlmacenamiento.getValue().toString())' 'memoriaBuffer.setText("10")' 'PREVIEW:' com.avilaos.gui.BufferMemoryGuiTest
"$JAVA_BIN/java" -Djava.awt.headless=true -cp "$TMP/base/classes:$DEPS" com.avilaos.gui.EscalaMetricasTest > "$OUT/escala-base.txt"
mutacion escala gui/PanelMetricas.java 'ejeUso.setAutoRange(true);' 'ejeUso.setRange(0, 100);' 'ESCALA:' com.avilaos.gui.EscalaMetricasTest
mutacion trazos gui/PanelMetricas.java 'new float[]{8f, 6f}' 'null' 'TRAZOS:' com.avilaos.gui.EscalaMetricasTest
# Repetir originales tras las mutaciones: también deben pasar.
"$JAVA_BIN/java" -Djava.awt.headless=true -cp "$TMP/base/classes:$DEPS" "$CLASS" | tee "$OUT/restaurado.txt"
printf 'Evidencia: %s\n' "$OUT"
