# Escenarios de carga JSON

Desde Inicio, cargar un archivo de `sistema-archivos/` en la tarjeta **Sistema de archivos** y uno de `procesos/` en **Procesos y configuración**. Reiniciar y volver a cargar el sistema original antes de repetir una prueba que modifique archivos.

## Sistema de archivos

| Archivo | Uso |
|---|---|
| `01-sistema-enunciado.json` | Ejemplo oficial: cabezal en 50 y ocho archivos. |
| `02-disco-vacio.json` | Disco sin archivos, con raíz. |
| `03-cabezal-invalido.json` | Rechazo por cabezal inicial fuera del rango de datos. |
| `04-sin-sistema-archivos.json` | Rechazo por ausencia de `fileSystem`. |
| `10-disco-ocupacion-agrupada.json` | Ocupación agrupada para inspeccionar el mapa. |
| `11-disco-lleno.json` | Disco de datos sin bloques libres. |
| `12-disco-disperso.json` | Archivos ubicados en bloques separados. |
| `13-arbol-complejo.json` | Jerarquía con directorios, archivos y permisos. |
| `14-disco-grande-800.json` | Carga de un disco de 800 bloques de datos. |
| `16-fs-cabezal-swap.json` | **Escenario 16 — 7 estados**: cabezal en bloque 193, archivos idénticos al `01`. Usar **exclusivamente** con `16-swap-suspension-p2.json`. |

## Procesos y configuración

| Archivo | Uso |
|---|---|
| `05-procesos-enunciado.json` | Ocho procesos del ejemplo oficial; usar con `01-sistema-enunciado.json`. |
| `06-procesos-vacios.json` | Computador con lista de procesos vacía. |
| `07-sin-computador.json` | Rechazo por falta de `computer`. |
| `08-json-malformado.json` | Rechazo por formato inválido. |
| `09-cambio-fcfs-prioridades.json` | Escenario para comprobar el cambio de política de CPU. |
| `15-sstf-caso-p2.json` | Ocho lecturas con SSTF sobre el sistema oficial. El orden debe observarse en el log: las solicitudes llegan en ciclos sucesivos. |
| `16-swap-suspension-p2.json` | **Escenario 16 — 7 estados completos**: demuestra Bloqueados/Suspendidos y Listos/Suspendidos. RAM=24, política **SSTF**. **Cargar `16-fs-cabezal-swap.json`** como sistema de archivos (cabezal en 193, así el swap-out termina antes que el READ al bloque 11, dejando al proceso visible en la cola de Bloqueados/Suspendidos antes de pasar a Listos/Suspendidos). |

Los dos ejemplos del enunciado se conservan sin modificar. El ejemplo de procesos contiene UPDATE sin `newFileName`; el simulador genera un nombre disponible y lo registra en el log. La carga o sustitución de JSON se rechaza si la simulación está iniciada, si hay solicitudes pendientes o si swap está ocupado.
