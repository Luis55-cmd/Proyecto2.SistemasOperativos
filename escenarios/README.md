# Escenarios reproducibles de carga JSON

Estos archivos son estados iniciales de ciclo 0 compatibles con **Cargar Estado**.
Cargar reemplaza la sesión actual. Las pruebas automatizadas invocan el mismo método del botón, sin replicar el parser.
No son checkpoints completos de ejecuciones interrumpidas.

| Archivo | Qué ejercita | Resultado observado en 80 ticks |
|---|---|---|
| 01-cpu-fcfs-rr.json | Dos procesos en FCFS y dos en RR, quantum 2 | Completan los cuatro; RAM liberada |
| 02-admision-ram.json | RAM128; proceso80 y proceso64 en NUEVO | El segundo entra al liberar el primero; RAM final0 |
| 03-entrada-salida.json | IO cada2 CPU, espera3 ticks, otro listo y otra CPU independiente | Se observa bloqueo y los tres completan |
| 04-productor-consumidor-remoto.json | Buffer2 en Host; consumidor local y productor remoto, latencia2; tres elementos cada uno | Se observan bloqueos/red; producidos3, consumidos3; RAM final del Host2 |
| 05-prioridad-primer-despacho.json | Baja1 insertado antes que Alta10 | Corregido: Alta ejecuta primero y Baja continúa después |

| 06-prioridad-apropiacion.json | Media ejecutando PC2, Baja delante de Alta en Listos | Alta desaloja; Media retoma PC3; Baja al final |

Los PASS son comprobaciones de los criterios listados, no certificación de toda la política ni de la semántica completa de instrucciones. El escenario05 reproducía el defecto y ahora pasa; scripts/verificar-prioridades.sh detecta su reintroducción y la desactivación del desalojo.

## Ejecutar sin crear nada a mano

En NetBeans:
1. Detener la ejecución anterior y ejecutar com.avilaos.Main desde proyecto1-gui.
2. Simulación → Cargar Estado → seleccionar uno de estos JSON.
3. Iniciar a 100 ms. Pausar después de al menos 80 ticks.
4. Si quieres compartir la ejecución visual, usar **Exportar log** y conservar el archivo.
5. Cargar el siguiente escenario vuelve al ciclo0. No es necesario recrear computadores, buffers ni procesos.

Los logs generados por el runner ya están en resultados/; no hace falta repetirlos manualmente.
La prueba visual mínima útil es cargar el escenario04 y confirmar que interfaz y métricas siguen la ejecución sin trabarse.

Para repetir automáticamente:
JAVA_HOME=<JDK25> MVN=<ruta-mvn> bash scripts/ejecutar-escenarios.sh
Se generan resultado.txt, un .log por escenario y un JSON final por escenario.
Los seis escenarios deben indicar PASS.

## Evidencia automática

El runner avanza el GlobalClock y drena el EDT entre ticks. Registra eventos reales del controlador y snapshots adicionales de RAM, colas, PCB, etapas de sincronización, demora de red y semáforos.
Los escenarios01–04 detectaron mutaciones compiladas en ejecución CPU, readmisión RAM, avance E/S y avance de red, respectivamente.
La eliminación del filtro de arreglos vacíos también fue detectada. Evidencia local: resultados/mutaciones/.
No se simula un clic con ratón: se invoca la misma función de producción que usa el botón.

## Estado real de guardar/cargar

- Funciona para estas configuraciones iniciales controladas.
- Se corrigió el buffer fantasma que aparecía al cargar buffers: [] (y el equivalente para computadores vacíos).
- El formato actual se interpreta con búsquedas de texto; depende del espaciado y no valida JSON de forma general.
- Cargar reinicia antes de validar completamente; un archivo inválido puede dejar una sesión parcial.
- restoreProcess acepta estados guardados y no garantiza admisión válida ante RAM insuficiente. El escenario02 declara NUEVO explícitamente.
- Guardar no escapa nombres especiales.
- No conserva contenido de buffers, colas de semáforos, etapa de sincronización, demoras pendientes ni contadores de métricas.
- Diagnóstico con productor real: guardar con un elemento y volver a cargar deja cero elementos.
- Por tanto **Guardar Estado no permite todavía pausar y continuar fielmente una ejecución cualquiera**.
- Los JSON finales del runner son evidencia del serializador actual; no se presentan como checkpoints reanudables.

## Archivos y funciones

- MonitorSimulacion.cargarEstado(File) y guardarEstado(File): mismas rutas para botones y runner. Devuelven éxito/fallo. La carga modifica toda la sesión; el guardado escribe archivo.
- EscenariosJsonRunner.main: carga seis archivos y ejecuta80 ticks por caso; termina tras la lista finita y escribe resultado.
- EscenariosJsonRunner.snapshot: observa estado, no ejecuta ni muta procesos.
- scripts/ejecutar-escenarios.sh: compila y lanza el runner con las dependencias de Maven.
