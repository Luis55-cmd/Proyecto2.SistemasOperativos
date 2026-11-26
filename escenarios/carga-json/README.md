# Escenarios para probar la carga de JSON

En la pantalla de **Inicio**, usa el botón **Cargar JSON** de cada tarjeta.
La carpeta está organizada en dos subdirectorios temáticos para facilitar la selección:

---

## 📁 `sistema-archivos/` (Tarjeta: Sistema de archivos)
Archivos JSON que configuran el disco (`diskConfig`: bloques y cabezal) y la jerarquía de directorios/archivos (`fileSystem`).

| Archivo | Qué prueba | Resultado esperado |
|---|---|---|
| `01-sistema-enunciado.json` | Ejemplo oficial del enunciado | Cabezal en 50, archivos en bloques 11, 34, 62, 64, 95, 119, 123, 180. |
| `02-disco-vacio.json` | Disco limpio | Cabezal en 50, solo la raíz creada. |
| `03-cabezal-invalido.json` | Caso inválido | Rechaza la carga porque el cabezal inicial (200) supera el límite. |
| `04-sin-sistema-archivos.json` | Caso inválido | Rechaza la carga por falta de la sección `fileSystem`. |
| `10-disco-ocupacion-agrupada.json` | Mapa agrupado | 110 de 200 bloques de datos ocupados en rangos continuos. |
| `11-disco-lleno.json` | Saturación de disco | 200 de 200 bloques ocupados: raíz, directorio y archivo de 198 bloques. |
| `12-disco-disperso.json` | Fragmentación | 20 archivos de un bloque separados a lo largo del disco. |
| `13-arbol-complejo.json` | Estructura jerárquica | 9 directorios, 8 archivos anidados, diferentes dueños y permisos. |
| `14-disco-grande-800.json` | Alta capacidad | Disco de 800 bloques de datos. |

---

## 📁 `procesos/` (Tarjeta: Procesos y configuración)
Archivos JSON que configuran la velocidad del ciclo, swap, política de disco, computador (`computer`: RAM, política CPU, quantum) y la lista de procesos con sus solicitudes de E/S.

| Archivo | Qué prueba | Resultado esperado |
|---|---|---|
| `05-procesos-enunciado.json` | Ejemplo del enunciado | 8 procesos asociados a los archivos del escenario `01` con política inicial FIFO. |
| `06-procesos-vacios.json` | Sin procesos | Configuración del computador con lista de procesos vacía. |
| `07-sin-computador.json` | Caso inválido | Rechaza la carga por falta de la sección `computer`. |
| `08-json-malformado.json` | Caso inválido | Rechaza el JSON con error de formato sintáctico. |
| `09-cambio-fcfs-prioridades.json` | Planificación CPU | FCFS durante 3 ciclos y cambio a prioridades apropiativas. |
| `15-sstf-caso-p2.json` | **Caso P2 SSTF del libro** | Política SSTF: atención exacta 62 → 64 → 34 → 11 → 95 → 119 → 123 → 180 sobre el escenario `01`. |

---

> **Regla de integridad:** Tras una carga válida, intentar cargar otra configuración pide confirmación para reemplazar los datos. La carga se rechaza automáticamente si la simulación ya está corriendo para no corromper el estado.
